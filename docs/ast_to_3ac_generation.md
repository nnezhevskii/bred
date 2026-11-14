# AST to 3AC Generation

This document describes the active lowering layer implemented by
`LLTACGenerator` and `LLTACExpressionSubgenerator`. It covers the pipeline
stage that turns the resolved, instantiated, semantically checked Bred AST into
low-level three-address code (`LLTACElement`).

The next layer, 3AC to C, is documented separately in
`docs/3ac_to_c_generation.md`.

## 1. Scope and Inputs

3AC generation runs after:

1. parsing into `ProgramRoot`;
2. context collection;
3. type resolution through `ProgramTypeResolver`;
4. template/typeclass instantiation through `TemplateInstantiator`;
5. semantic analysis through `SemanticAnalyzer.analyzeWithResult`.

The generator requires:

- a resolved AST;
- `ProgramGlobalContext`, with concrete type, constructor, product, sum, and
  extern/function metadata;
- `expressionTypes`, the identity-based semantic type table produced by
  semantic analysis.

The generator does not redo semantic validation. It assumes semantic analysis
has already rejected bad lvalues, wrong branch coverage, unknown fields,
wrong constructor arguments, and unsupported value-producing `when` usage.

## 2. Core 3AC Structures

Every generated item implements `LLTACElement`:

| Element | Meaning |
|---------|---------|
| `LLTACFunc(name, type)` | Function boundary and return type. Printed as `func name`. |
| `LLTACLabel(name)` | Control-flow target. |
| `LLTACInstruction(opcode, destination, arg1, arg2)` | Ordinary 3AC instruction. |
| `LLTACRetInstruction(value)` | Return instruction. `value == null` means `Unit`. |
| `LLTACGlobalStaticArray(name, type, size, initialValues)` | Top-level static array declaration. |

`Operand` variants:

| Operand | Role |
|---------|------|
| `Variable(name, type)` | Left and right value. Used for locals, params, fields by name, arrays, and owner paths such as `person.address`. |
| `IntConst`, `LongConst`, `DoubleConst`, `BoolConst`, `StringConst` | Literal right values. |
| `TypeConst(type)` | Metadata operand for struct and sum definitions. |
| `FunctionCall(name, type)` | Function/constructor target for `CALL`. |
| `Label(label)` | Jump target. |

Temporary names come from `TACNameEmitter`:

- variables: `var1`, `var2`, ...;
- labels: `lbl1`, `lbl2`, ....

The emitter is shared across the whole `LLTACGenerator` instance. Numbering is
therefore deterministic for one traversal but intentionally not scoped per
function.

## 3. Global Traversal Order

`ProgramRoot` lowers in this order:

1. type declarations;
2. global variables;
3. functions.

This order matters for the C backend:

- struct metadata appears before function bodies;
- global static arrays appear before function prototypes;
- function definitions appear after type/global metadata.

Typeclasses and instances are not visited by the root traversal. The active
pipeline expects `TemplateInstantiator` to clone needed instance methods into
ordinary functions and remove runtime relevance from raw typeclass constructs.

## 4. Function and Local State

For every `FunctionDeclAstNode`:

1. `localVariableTypes` is cleared.
2. The function name is registered in `mangledFunctions` under the source name.
3. `LLTACFunc(functionName, resultType)` is emitted.
4. Each argument is put into `localVariableTypes`.
5. Each argument emits `GET_PARAM name:type`.
6. The function body is lowered.

Current function name mangling inside `LLTACGenerator` is identity:
`mangleFunctionName(function) = function.name`. Generic and instance method
names are expected to have already been rewritten by `TemplateInstantiator`.

`localVariableTypes` is used only by field-access lowering. It tracks function
arguments, scalar locals, local arrays, and temporary branch bindings for sum
payload patterns.

## 5. Type Declarations

### 5.1 Product Types

`DeclareProductTypeASTNode` lowers to:

```text
STRUCT_DEF ProductType
FIELD field1:Type1
FIELD field2:Type2
STRUCT_DEF_END
```

Fields are read from `globalContext.productFields(type)`, not directly from the
AST node. This is important for resolved/instantiated generic products.

Edge cases:

- mutability is not represented in 3AC; it is semantic-only;
- product constructor calls are ordinary `CALL` instructions and are recognized
  later by the C backend when call name equals destination type name.

### 5.2 Sum Types

`DeclareSumTypeASTNode` lowers to struct metadata plus sum variant metadata.

For:

```bred
type Response = Success(value: Int, message: String) | Failure
```

3AC shape:

```text
STRUCT_DEF Response
FIELD tag:Int
FIELD Success_value:Int
FIELD Success_message:String
STRUCT_DEF_END
SUM_VARIANT Response Success:Response 0:Int
SUM_VARIANT_FIELD Response Success:Response Success_value:Int
SUM_VARIANT_FIELD Response Success:Response Success_message:String
SUM_VARIANT Response Failure:Response 1:Int
```

Rules:

- every sum struct has `tag:Int`;
- payload fields are flattened into the owner struct;
- flattened field name is `<Variant>_<field>`;
- type-argument payloads use synthetic field names from context, such as
  `value0`, so `Just<Int>` becomes `Just_value0`;
- variant tags are declaration indexes.

If `globalContext.sumDefinition(type)` is absent, the generator emits nothing
for that sum declaration. In the normal resolved pipeline this should not
happen.

## 6. Global Variables

Only top-level static arrays lower to 3AC:

```text
global values:Array[3] [1:Int, 2:Int, 3:Int]
```

Rules:

- `DeclareGlobalVariableASTNode.size != null` emits `LLTACGlobalStaticArray`;
- element type is the declared type normalized to `Array<T>`;
- initializer is accepted only when it is an array literal;
- initializer elements may be literal `Boolean`, `Double`, `Int`, `Long`, or
  `String`;
- non-literal global array initializer elements throw a TAC generation error;
- scalar top-level variables are currently ignored by 3AC generation.

Completeness note: semantic analysis supports top-level scalar constants, but
this layer does not emit C storage for them. A referenced scalar global can
therefore escape as an undeclared C identifier later. Top-level arrays are the
only active global storage path.

## 7. Statements

### 7.1 Block

`BlockAstNode` lowers by concatenating every statement's 3AC.

There is no explicit block/scope marker in 3AC. Local names must already be
safe for C-level function scope.

### 7.2 Scalar Local Declaration

`ScalarVariableInitializationASTNode(name, type, expression, isMutable)`:

1. records `name -> type` in `localVariableTypes`;
2. lowers the initializer with preferred destination `name`;
3. if the initializer final variable differs from `name`, emits
   `ASSIGN name:type final:type`.

Mutability is not represented in 3AC.

### 7.3 Local Static Array Declaration

`ArrayDeclarationASTNode`:

```text
ALLOC name:Array<T> T size
```

Then, if the initializer is an array literal, for each element:

```text
ASSIGN indexTmp:Int i:Int
<element expression into valueTmp>
STORE array:Array<T> indexTmp:Int valueTmp:T
```

Rules and edge cases:

- arrays are function-local static C arrays in the next layer;
- array initializer expressions are only lowered in declaration context;
- `ArrayInitializationExpressionASTNode` used as a standalone expression is a
  TAC generation error;
- array size/type checks are semantic-phase responsibilities.

### 7.4 Assignment

Assignment dispatches by lvalue kind.

#### Variable lvalue

`x = expr` lowers `expr` with preferred destination `x`. If expression lowering
returns another final variable, emits an extra `ASSIGN x final`.

#### Array element lvalue

`arr[index] = expr` lowers:

```text
<index expression into indexTmp:Int>
<rhs expression into valueTmp:T>
STORE arr:Array<T> indexTmp:Int valueTmp:T
```

The array type is reconstructed as `Array<rhsType>`.

#### Product field lvalue

`person.address.city = expr` lowers:

```text
<rhs expression into tmp>
STORE_FIELD person.address:Address city:String tmp:String
```

The owner path and field type are computed from `localVariableTypes` and
`globalContext.productFields`.

Unsupported lvalue kinds throw `unexpected lvalue for TAC generation`.

### 7.5 Call Statement

`CallFunctionStatementAstNode(expression)` uses the analyzed result type
(`expression.tacType()`) for the call target. It passes `variable = null` to
discard the result, so `readInt()` as a statement emits `CALL readInt:Int 0:Int`.

This is valid for function calls with no result destination. Literal/calc
expressions in statement position are not expected by parser/semantic rules and
can fail because many expression forms require a destination.

### 7.6 If Statement

`if (condition) thenBlock else elseBlock` lowers:

```text
<condition into condTmp:Boolean>
JMP_IF_NOT elseLabel condTmp:Boolean
<then block>
JMP endLabel        ; only when else exists
elseLabel:
<else block>        ; only when else exists
endLabel:           ; or elseLabel when there is no else
```

No phi/join values exist; this is statement-level control flow only.

### 7.7 While Statement

`while (condition) body` lowers:

```text
beginLabel:
<condition into condTmp:Boolean>
JMP_IF_NOT endLabel condTmp:Boolean
<body>
JMP beginLabel
endLabel:
```

### 7.8 For Statement

`ForStatementAstNode` already contains desugared block content from parsing.
The generator simply visits `desugaredContent`.

The active desugaring is documented in `docs/grammar.md`; the generator does
not know original `for` bounds.

### 7.9 Return Statement

`return` lowers to:

```text
RET Unit
```

`return expr` lowers:

```text
<expr into tmp>
RET tmp:Type
```

The explicit/synthetic flag is not represented in 3AC. Control-flow checks have
already happened in semantic analysis.

### 7.10 When Statement

Only statement-form `when` is lowered. `WhenExpressionASTNode` in expression
position is rejected by the expression subgenerator.

The subject is lowered once:

```text
<subject into subjectTmp:SubjectType>
```

Then every branch is emitted in source order.

#### Sum-typed branch

For a branch matching a sum variant:

```bred
Just(value) -> { body }
```

lowering shape:

```text
LOAD_FIELD actualTag:Int subjectTmp:Maybe_Int tag:Int
ASSIGN expectedTag:Int <variantTag>:Int
EQ cond:Boolean actualTag:Int expectedTag:Int
JMP_IF_NOT nextLabel cond:Boolean
LOAD_FIELD value:T subjectTmp:Maybe_Int Just_value:T
<body>
JMP endLabel
nextLabel:
```

Bare variant branches such as `Just ->` are the same tag check without payload
loads. Payload binding is positional and follows the flattened sum field names.

The generator temporarily records payload binding names in `localVariableTypes`
while lowering the branch body, then restores previous entries. This supports
field access on payload-bound product values during that branch.

Semantic analysis enforces:

- known variants;
- no duplicate variants;
- exhaustiveness unless `else` exists;
- payload arity;
- simple variable names;
- no binding name duplicates/redeclarations.

#### Non-sum branch

For non-sum subjects, or non-pattern conditions:

```text
<condition into branchValue>
EQ cond:Boolean subjectTmp:SubjectType branchValue:SubjectType
JMP_IF_NOT nextLabel cond:Boolean
<body>
JMP endLabel
nextLabel:
```

#### Else branch

`else -> body` lowers to:

```text
<body>
JMP endLabel
```

The final `endLabel` is emitted after all branches.

Completeness note: if semantic analysis failed to enforce sum exhaustiveness,
3AC would still emit a fallthrough path to the end label without executing any
branch. Exhaustiveness is intentionally a semantic-layer invariant, not a TAC
layer invariant.

## 8. Expressions

Expression lowering returns:

```kotlin
LLTACExpressionSubgeneratorResult(
    instructions: List<LLTACElement>,
    finalVariable: String?,
    finalType: TypeSign,
)
```

Most expression forms need a destination variable. Variables and call
statements may have no destination.

### 8.1 Literals

`Int`, `Long`, `Double`, `Boolean`, and `String` literals lower to `ASSIGN`:

```text
ASSIGN dest:Int 1:Int
ASSIGN dest:Long 1:Long
ASSIGN dest:String text:String
```

Long assignment uses `Operand.LongConst` when destination type is `Long`;
otherwise integer-looking values use `Operand.IntConst`.

### 8.2 Variable Expression

For an ordinary variable:

```text
; no instruction
finalVariable = variableName
```

For a visible zero-argument constructor, such as a nullary sum variant:

```text
CALL dest:OwnerType Constructor:OwnerType 0:Int
```

The constructor path requires a destination.

### 8.3 Binary Expression

Both operands are lowered into temporaries, then mapped by operator:

| Bred operator | 3AC opcode |
|---------------|------------|
| `+` | `ADD` |
| `-` | `SUB` |
| `*` | `MUL` |
| `/` | `DIV` |
| `%` | `MOD` |
| `==` | `EQ` |
| `!=` | `NEQ` |
| `<` | `LT` |
| `>` | `GT` |
| `<=` | `LE` |
| `>=` | `GE` |
| `&&` | `AND` |
| `||` | `OR` |

Shape:

```text
<left into leftTmp>
<right into rightTmp>
OP dest:ResultType leftTmp:LeftType rightTmp:RightType
```

No short-circuit lowering exists for `&&` or `||`; both operands are lowered
before `AND`/`OR`.

### 8.4 Unary Expression

| Bred operator | 3AC opcode |
|---------------|------------|
| `!` | `NOT` |
| unary `-` | `NEG` |

Shape:

```text
<operand into tmp>
NOT dest:Boolean tmp:Boolean
NEG dest:T tmp:T
```

### 8.5 Function and Constructor Call

Arguments are lowered left-to-right into temporaries, then emitted as `PARAM`
instructions in the same order, followed by `CALL`.

```text
<arg1 into tmp1>
<arg2 into tmp2>
PARAM tmp1:T1
PARAM tmp2:T2
CALL dest:Result functionName:Result 2:Int
```

Name resolution:

- `mangledFunctions[sourceName]` is used when available;
- otherwise the AST call name is emitted;
- product and sum constructor calls are still `CALL`; C generation interprets
  them using destination type plus metadata.

Calls in statement position have an empty destination and retain their return type.

```text
CALL println:Unit 1:Int
CALL readInt:Int 0:Int
```

### 8.6 Array Element Access

`arr[index]` lowers:

```text
<index into indexTmp:Int>
LOAD dest:T arr:Array<T> indexTmp:Int
```

The result destination is currently a fresh temporary, not the preferred
destination passed into expression lowering. Assignment/return callers use the
returned final variable.

### 8.7 Product Field Access

`person.address.city` lowers:

```text
LOAD_FIELD dest:String person.address:Address city:String
```

The generator resolves each intermediate field through product metadata:

1. root type comes from `localVariableTypes`;
2. every intermediate field must itself be a product type;
3. final field type becomes result type.

Missing local root, missing product metadata, or missing fields are TAC
generation errors. In the normal pipeline, semantic analysis should have
already rejected these cases.

### 8.8 Array Initializer Expression

`ArrayInitializationExpressionASTNode` is not a general expression in 3AC.
It must be consumed by an `ArrayDeclarationASTNode`. Direct expression lowering
throws:

```text
array initializer should be lowered by ArrayDeclarationASTNode
```

### 8.9 When Expression

Value-producing `when` is not supported by this layer. Expression lowering for
`WhenExpressionASTNode` throws:

```text
when expression TAC lowering is not supported yet
```

Statement-form `WhenStatementAstNode` is supported as described above.

## 9. Complete AST Coverage Matrix

| AST node | 3AC behavior |
|----------|--------------|
| `ProgramRoot` | Visits types, globals, functions in order. |
| `DeclareProductTypeASTNode` | Emits struct definition metadata. |
| `DeclareSumTypeASTNode` | Emits tagged struct metadata plus sum variant metadata. |
| `SumTypeVariantASTNode` | Not visited directly; consumed through sum definition metadata. |
| `SumTypeVariantFieldASTNode` | Not visited directly; consumed through sum definition metadata. |
| `FunctionDeclAstNode` | Emits function boundary, params, body. |
| `FunctionArgument` | Emits `GET_PARAM` when part of function declaration. |
| `BlockAstNode` | Concatenates statement lowering. |
| `ScalarVariableInitializationASTNode` | Emits initializer and optional assign. |
| `ArrayDeclarationASTNode` | Emits `ALLOC` and initializer stores. |
| `DeclareGlobalVariableASTNode` | Emits only top-level static arrays; scalar globals ignored. |
| `AssignmentStatementAstNode` | Dispatches to variable, array element, or field store. |
| `CallFunctionStatementAstNode` | Emits a `CALL` with the expression's result type and an empty destination. |
| `ReturnFunctionStatementAstNode` | Emits `RET`. |
| `IfStatementAstNode` | Emits conditional jumps and labels. |
| `WhileStatementAstNode` | Emits loop labels and jumps. |
| `ForStatementAstNode` | Visits parser-desugared block. |
| `WhenStatementAstNode` | Emits statement pattern matching / equality dispatch. |
| `WhenBranchAstNode` | Not visited directly; consumed by `WhenStatementAstNode`. |
| `TypeClassDeclAstNode` | Ignored by root traversal. Expected to be handled by template instantiation. |
| `TypeClassMethodDeclAstNode` | Ignored directly. |
| `InstanceDeclAstNode` | Ignored directly. Instantiated methods should already be ordinary functions. |
| `EmptyNode` | No-op. |
| `IntLiteralExpressionASTNode` | `ASSIGN` literal. |
| `LongLiteralExpressionASTNode` | `ASSIGN` literal. |
| `DoubleLiteralExpressionASTNode` | `ASSIGN` literal. |
| `BooleanLiteralExpressionASTNode` | `ASSIGN` literal. |
| `StringLiteralExpressionASTNode` | `ASSIGN` literal. |
| `VariableExpressionASTNode` | No instruction, or zero-arg constructor `CALL`. |
| `FunctionCallExpressionASTNode` | Argument lowering, `PARAM`, `CALL`. |
| `BinaryExpressionASTNode` | Operand lowering plus binary opcode. |
| `UnaryExpressionASTNode` | Operand lowering plus unary opcode. |
| `ArrayElementAccessASTNode` | Index lowering plus `LOAD`. Also accepted as assignment lvalue. |
| `FieldAccessChainExpressionASTNode` | `LOAD_FIELD`. Also accepted as assignment lvalue via `STORE_FIELD`. |
| `ArrayInitializationExpressionASTNode` | Only supported inside array declarations/global arrays. Direct expression lowering errors. |
| `WhenExpressionASTNode` | Statement-only. Direct expression lowering errors. |

## 10. Edge Cases and Invariants

- The layer is intentionally not exhaustive over invalid programs; semantic
  analysis owns validity.
- `when` exhaustiveness is not rechecked here.
- There are no phi nodes or SSA semantics; variables are mutable names.
- 3AC has no lexical block markers. C locals are function-scoped later.
- Sum payload binding names are temporarily inserted into `localVariableTypes`
  only while lowering that branch.
- Product/sum values are represented as by-value structs, not pointers.
- Static array sizes are storage-layer details, not part of `TypeSign`.
- Local and global static arrays are supported; arrays of arrays are not
  intentionally supported by the backend.
- Typeclass and instance declarations should not need direct lowering after
  template instantiation.
- `mangleFunctionName` is identity in this layer; if a new feature introduces
  runtime overloads after instantiation, it must ensure names are already
  unique before 3AC generation.

## 11. Test Coverage Pointers

Primary coverage lives in:

- `tac/src/test/kotlin/org/nnezh/lltag/LLTAGCompileExpectationsTest.kt`
  for direct expectations around failures, arrays, products, sums, `when`,
  generics, and TAC shape;
- `tac/src/test/kotlin/org/nnezh/lltag/TACInstructionSequenceTest.kt` for typed
  instruction sequences derived from the programs in `tac/src/test/resources`;
- `tac/src/test/resources/deploymentHealthRadar.bred` for a compact fixture
  covering product fields, generic products/sums, and sum payload patterns.

