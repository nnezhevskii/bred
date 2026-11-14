# 3AC to C Generation

This document describes the active C backend implemented by `CTranspile`.
It covers the layer that turns `LLTACElement` instructions into C source lines.

The previous layer, AST to 3AC, is documented separately in
`docs/ast_to_3ac_generation.md`.

## 1. Scope and Inputs

`CTranspile.compile(instructions)` accepts a list of low-level 3AC elements.
It assumes those elements were produced by `LLTACGenerator` from a resolved,
instantiated, semantically checked AST.

The backend is a renderer, not a verifier. It validates only operand shapes
needed to render an instruction and throws `CTranspileException` for malformed
TAC. Semantic correctness, branch exhaustiveness, mutability, and type equality
belong to earlier stages.

The returned value is a list of C source lines without `runtime.c` prepended.
The root executable builder prepends runtime support later.

## 2. Output Phases

`compile` emits C in four phases:

1. collect and render struct definitions;
2. collect and render global static arrays;
3. collect and render function prototypes;
4. stream through TAC to render function bodies, labels, returns, and
   statements.

The ordering is intentional:

```text
typedef struct ...
global arrays...
function prototypes...
function bodies...
```

At the end, the compiler appends one closing brace for the last function body.
Between functions, `parseFunction` emits the previous function's closing brace.

## 3. C Type Mapping

Two type renderers exist because array types mean different things in
declarations and arguments.

### 3.1 Scalar/storage rendering

Used for local scalar declarations, struct fields, global array elements, and
compound literals.

| `TypeSign` | C |
|------------|---|
| `Boolean` | `bool` |
| `Double` | `double` |
| `Long` | `long long` |
| `Int` | `int` |
| `String` | `String` |
| `Unit` | `void` |
| resolved non-primitive user type | `type.toManglePart()` |
| `Array<T>` | error as scalar declaration |

### 3.2 Argument rendering

Used for function prototypes and function definitions.

| `TypeSign` | C |
|------------|---|
| primitive/user scalar | same as storage rendering |
| `Array<T>` | `<T-as-argument>*` |

Example:

```text
Array<Int> -> int*
Array<Long> -> long long*
```

## 4. Metadata Collection

### 4.1 Struct Definitions

The backend scans for:

```text
STRUCT_DEF Type
FIELD field1:T1
FIELD field2:T2
STRUCT_DEF_END
```

and renders:

```c
typedef struct Type { T1 field1; T2 field2; } Type;
```

Rules:

- a `STRUCT_DEF` must be followed only by `FIELD` instructions and then
  `STRUCT_DEF_END`;
- unexpected instructions inside a struct definition throw
  `CTranspileException`;
- struct metadata instructions themselves produce no lines during the later
  streaming pass.

### 4.2 Sum Definitions

The backend separately scans all `SUM_VARIANT` and `SUM_VARIANT_FIELD`
instructions into `SumDefinition` metadata:

```text
SUM_VARIANT Owner Variant:Owner tag:Int
SUM_VARIANT_FIELD Owner Variant:Owner FlattenedField:T
```

This metadata is used only when rendering `CALL` instructions whose destination
type is a known sum owner and whose call name is a known variant.

Example:

```text
CALL response:Response Success:Response 2:Int
```

with metadata renders:

```c
response = (Response){.tag = 0, .Success_value = code, .Success_message = message};
```

The backend expects the 3AC generator to supply matching argument counts and
field order. `renderSumConstructor` zips fields with argument names and does not
perform semantic arity validation.

### 4.3 Global Static Arrays

Every `LLTACGlobalStaticArray` renders before prototypes:

```c
int values[3] = {1, 2, 3};
int values[2];
```

Rules:

- element type is `array.type.elementTypeOrNull`;
- `initialValues == null` means no initializer;
- empty initializer list also renders no initializer;
- string elements render as runtime `String` aggregate values:
  `{"escaped", originalLength}`;
- non-string elements render through `RightValue.asRightValue()`.

Malformed global array type, for example a non-array type, throws
`CTranspileException`.

### 4.4 Function Prototypes

The backend scans every `LLTACFunc`, then immediately following `GET_PARAM`
instructions, and emits a prototype:

```text
func sum
GET_PARAM a:Int
GET_PARAM b:Int
```

renders:

```c
int sum(int a,int b);
```

`main` is special:

```c
int main(int size, char** arg);
```

The Bred source signature is not used for C `main`; the runtime-facing C entry
point always receives `(int size, char** arg)`.

## 5. Function Body Emission

`LLTACFunc` starts a new function body.

For non-main functions:

```c
<returnType> name(<params>) {
```

For `main`:

```c
int main(int size, char** arg) {
init_runtime();
```

`GET_PARAM` instructions are consumed while opening the function and do not
emit body lines.

### 5.1 Local Declaration Scan

Before rendering body instructions, `parseFunction` scans from the current TAC
position until the next `LLTACFunc` and emits local declarations.

It collects:

- scalar destination variables from most instructions;
- local static arrays from `ALLOC`;
- no declarations for `PARAM`, struct/sum metadata, or `STORE_FIELD`;
- no scalar declaration from `STORE`;
- no scalar declaration for array-typed destinations except `ALLOC`.

Examples:

```text
ALLOC arr:Array<Int> Int 3
ASSIGN i:Int 0:Int
LOAD read:Int arr:Array<Int> i:Int
```

renders declarations:

```c
int arr[3];
int i, read;
```

All C locals are function-scoped. There is no block-scope declaration model in
the backend.

## 6. Element Mapping

### 6.1 `LLTACFunc`

Starts a function body and emits local declarations as described above.

### 6.2 `LLTACLabel`

```text
lbl1
```

renders:

```c
lbl1:
```

### 6.3 `LLTACRetInstruction`

```text
RET Unit
RET value:Int
```

renders:

```c
return;
return value;
```

Note: `main` is rendered as returning `int`, but `RET Unit` still renders
`return;`. Current tests lock in this behavior.

### 6.4 `LLTACGlobalStaticArray`

Rendered in the global array phase and skipped during streaming.

## 7. Opcode Mapping

### 7.1 `ASSIGN`

Shape:

```text
ASSIGN destination:T value:T
```

Non-string destination:

```c
destination = value;
```

String destination:

```c
destination = create_string("escaped", strlen("escaped"));
```

String escaping handles backslash, quote, newline, carriage return, and tab.

Constants:

- `LongConst(7)` renders as `7LL`;
- `BoolConst(true)` renders as `true`;
- `DoubleConst` uses Kotlin `toString()`;
- `StringConst` is raw until escaped by string assignment/global initializer.

### 7.2 `GET_PARAM`

Consumed while opening the function. Emits no body line.

If encountered during ordinary instruction streaming, it is a no-op.

### 7.3 `PARAM`

No direct output. It is consumed indirectly by the following `CALL`.

The `CALL` renderer looks backward from the call instruction and expects the
last `amountOfArgs` instructions to be `PARAM` instructions with variable
destinations.

### 7.4 Binary Operations

These opcodes share `generateSimpleOperation`:

| Opcode | C |
|--------|---|
| `ADD` | `left=arg1+arg2;` |
| `SUB` | `left=arg1-arg2;` |
| `MUL` | `left=arg1*arg2;` |
| `DIV` | `left=arg1/arg2;` |
| `MOD` | `left=arg1%arg2;` |
| `EQ` | `left=arg1==arg2;` |
| `NEQ` | `left=arg1!=arg2;` |
| `LT` | `left=arg1<arg2;` |
| `GT` | `left=arg1>arg2;` |
| `LE` | `left=arg1<=arg2;` |
| `GE` | `left=arg1>=arg2;` |
| `AND` | `left=arg1&&arg2;` |
| `OR` | `left=arg1||arg2;` |

Special case:

```text
ADD line:String a:String b:String
```

renders:

```c
line=concat(a,b);
```

No special string equality lowering exists here. String equality should use the
runtime function surface exposed to earlier stages, not `EQ` on `String`.

### 7.5 `CALL`

Shape:

```text
PARAM a:T1
PARAM b:T2
CALL dest:Result functionName:Result 2:Int
```

The backend recovers call arguments from preceding `PARAM` instructions in
source order.

#### Ordinary call with destination

```c
dest = functionName(a,b);
```

#### Ordinary call without destination

```c
functionName(a,b);
```

#### Product constructor call

If:

- destination exists;
- destination type is a resolved non-primitive non-array type;
- call name equals destination type name;
- call is not recognized as a sum variant;

then:

```text
CALL person:Person Person:Person 2:Int
```

renders:

```c
person = (Person){name,age};
```

Product constructor fields are positional.

#### Sum constructor call

If destination type has sum metadata and call name is a variant:

```text
CALL present:MaybeInt Just:MaybeInt 1:Int
CALL absent:MaybeInt Nothing:MaybeInt 0:Int
```

renders:

```c
present = (MaybeInt){.tag = 1, .Just_value0 = value};
absent = (MaybeInt){.tag = 0};
```

Sum payload fields are designated by flattened field names from
`SUM_VARIANT_FIELD`.

### 7.6 `NOT` and `NEG`

```text
NOT dest:Boolean arg:Boolean
NEG dest:T arg:T
```

render:

```c
dest=!arg;
dest=-arg;
```

### 7.7 `JMP_IF_NOT` and `JMP`

```text
JMP_IF_NOT lbl cond:Boolean
JMP lbl
```

render:

```c
if (!cond)goto lbl;
goto lbl;
```

Labels themselves render as `lbl:`.

### 7.8 `ALLOC`

No body statement is emitted. `ALLOC` contributes only a local static array
declaration during the function-body pre-scan:

```text
ALLOC values:Array<Int> Int 3
```

renders:

```c
int values[3];
```

Arrays of arrays are explicitly not supported in this backend.

### 7.9 `LOAD` and `STORE`

`LOAD` (`LLTAC_LDX`):

```text
LOAD dest:T array:Array<T> index:Int
```

renders:

```c
dest=array[index];
```

`STORE` (`LLTAC_STX`):

```text
STORE array:Array<T> index:Int source:T
```

renders:

```c
array[index]=source;
```

### 7.10 `LOAD_FIELD` and `STORE_FIELD`

`LOAD_FIELD`:

```text
LOAD_FIELD dest:T owner:Owner field:T
```

renders:

```c
dest = owner.field;
```

`owner` may be a dotted owner path stored in the operand name, for example
`person.address`.

`STORE_FIELD`:

```text
STORE_FIELD owner:Owner field:T source:T
```

renders:

```c
owner.field = source;
```

The backend does not distinguish product fields from flattened sum payload
fields here. Both are ordinary struct field accesses.

### 7.11 Struct and Sum Metadata Opcodes

These opcodes produce no body lines during streaming:

- `STRUCT_DEF`;
- `STRUCT_DEF_END`;
- `FIELD`;
- `SUM_VARIANT`;
- `SUM_VARIANT_FIELD`.

They are consumed by earlier collection passes.

## 8. Complete Opcode Coverage Matrix

| 3AC element/opcode | C backend behavior |
|--------------------|--------------------|
| `LLTACFunc` | Opens function body, emits locals, handles special `main`. |
| `LLTACLabel` | Emits `<label>:`. |
| `LLTACRetInstruction` | Emits `return;` or `return value;`. |
| `LLTACGlobalStaticArray` | Renders before prototypes, skipped in stream. |
| `GET_PARAM` | Used for prototypes/function params; no body line. |
| `ALLOC` | Local static array declaration only; no body line. |
| `PARAM` | No body line; consumed by following `CALL`. |
| `ASSIGN` | Scalar assignment or runtime string creation. |
| `ADD` | Numeric `+` or string `concat`. |
| `SUB` | `-`. |
| `MUL` | `*`. |
| `DIV` | `/`. |
| `MOD` | `%`. |
| `EQ` | `==`. |
| `NEQ` | `!=`. |
| `LT` | `<`. |
| `GT` | `>`. |
| `LE` | `<=`. |
| `GE` | `>=`. |
| `AND` | `&&`. |
| `OR` | `||`. |
| `CALL` | Ordinary call, product compound literal, or sum designated compound literal. |
| `RET` opcode inside `LLTACInstruction` | No-op; active return representation is `LLTACRetInstruction`. |
| `NOT` | `!`. |
| `NEG` | unary `-`. |
| `JMP_IF_NOT` | `if (!cond)goto label;`. |
| `JMP` | `goto label;`. |
| `LOAD` | `dest=array[index];`. |
| `STORE` | `array[index]=source;`. |
| `LOAD_FIELD` | `dest = owner.field;`. |
| `STORE_FIELD` | `owner.field = source;`. |
| `STRUCT_DEF` | Metadata only. |
| `STRUCT_DEF_END` | Metadata only. |
| `FIELD` | Metadata only. |
| `SUM_VARIANT` | Metadata only. |
| `SUM_VARIANT_FIELD` | Metadata only. |

## 9. Edge Cases and Invariants

### 9.1 Malformed TAC

Operand shape helpers throw `CTranspileException` with the expected role:

- `expectLeftValue`;
- `expectRightValue`;
- `expectFunctionCall`;
- `expectTypeConst`;
- `expectIntConst`;
- `expectLabel`;
- `expectVariable`.

`parseInstruction` wraps failures with:

- instruction index;
- opcode;
- full instruction text;
- underlying message.

Malformed struct definitions are also rejected during the struct collection
phase.

### 9.2 Constructor Recognition

Constructor recognition is based on TAC shape and metadata:

1. Sum constructor: destination type is in collected sum metadata and call name
   is a variant.
2. Product constructor: destination exists, type is user-defined, and call name
   equals destination type name.
3. Otherwise ordinary function call.

This means constructor arity and field typing are trusted from earlier stages.

### 9.3 Local Declaration Completeness

The backend declares local variables by scanning destinations before rendering.
This works for current 3AC because every local scalar that needs storage appears
as an instruction destination.

Important details:

- variables used only as function parameters are declared in the function
  signature, not as locals;
- branch payload bindings appear as `LOAD_FIELD` destinations and are therefore
  declared as C locals;
- `PARAM` destinations are skipped so argument temporaries are not redeclared
  just because they are passed;
- `STORE_FIELD` destinations are skipped because they are owners, not newly
  assigned variables;
- array names are declared only from `ALLOC`.

### 9.4 Strings

Runtime `String` is expected to be supplied by `runtime.c`.

The backend emits:

- string literal assignment as `create_string(...)`;
- string concatenation as `concat(a,b)`;
- global string array elements as `{ "text", length }`.

Escaping is handled consistently for string assignment and global string
initializers.

### 9.5 Main Function

C `main` is always:

```c
int main(int size, char** arg)
```

The body starts with:

```c
init_runtime();
```

The current backend still renders `RET Unit` as `return;` even in `int main`.
Tests currently lock this in.

### 9.6 Arrays

Local static arrays:

- declared from `ALLOC`;
- accessed through `LOAD`/`STORE`;
- no runtime allocation line is emitted.

Global static arrays:

- rendered before prototypes;
- may have literal initializer values;
- empty initializer lists render the same as no initializer.

Array arguments:

- rendered as pointers in function signatures.

Arrays of arrays:

- not supported by the backend.

### 9.7 Sum Values

Sum values are by-value tagged structs:

```c
typedef struct MaybeInt { int tag; int Just_value0; } MaybeInt;
```

There is no C `union`, heap allocation, or pointer model. Every payload field
for every variant exists in the same struct.

`when` lowering is already just ordinary TAC:

- `LOAD_FIELD` for `tag`;
- compare with expected tag;
- `JMP_IF_NOT`;
- optional `LOAD_FIELD` for payload bindings.

The C backend therefore has no special `when` construct.

### 9.8 Completeness Boundaries

The C backend is complete for the active `LLTACOperation` enum. It is not a
general C optimizer or verifier.

Open or intentionally limited areas:

- no arrays-of-arrays support;
- no block-scoped locals;
- no value-producing `when` representation;
- no runtime validation for function/constructor arity;
- no special string comparison lowering;
- scalar top-level globals do not reach this layer because AST -> 3AC does not
  emit them.

## 10. Test Coverage Pointers

Primary coverage lives in:

- `c-backend/src/test/kotlin/org/nnezh/compiler/CTranspileTest.kt` for direct
  opcode-to-C rendering, prototypes, arrays, strings, `Long`, products, sums,
  and malformed TAC diagnostics;
- `src/test/kotlin/org/nnezh/root/BredCompilationFactoryTest.kt` for root
  frontend-to-C integration, stdlib injection, global arrays, product/sum code,
  and the `examples/3ac.bred` deployment health radar payload extraction;
- `deploymentHealthRadar` in `TACInstructionSequenceTest` for checks that
  sum payload binding reaches ordinary `LOAD_FIELD` instructions before C
  rendering.

