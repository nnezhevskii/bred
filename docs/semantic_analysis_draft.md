# bred - Semantic Analysis

This document describes the current `analyzer` module. The older semantic code
under the main source tree is intentionally out of scope.

Source of truth:

- `analyzer/src/main/java/org/nnezh/SemanticAnalyzer.kt`
- `analyzer/src/main/java/org/nnezh/TypeValidator.kt`
- `analyzer/src/main/java/org/nnezh/SemanticError.kt`
- `analyzer/src/test/kotlin/org/nnezh/SemanticAnalyzerTest.kt`
- `analyzer/src/test/kotlin/org/nnezh/SemanticAnalyzerTestSupport.kt`
- shared context and signatures in `common` and `parser/src/main/.../context`
- compiler-wide type ownership in `docs/type_system.md`

## 1. Public API

```kotlin
class SemanticAnalyzer(val globalContext: ProgramGlobalContext) {
    fun analyze(root: ProgramRoot): Either<List<SemanticError>, List<SemanticError.SemanticWarning>>
    fun analyzeWithResult(root: ProgramRoot): Either<List<SemanticError>, SemanticAnalysisResult>
}
```

Result shape:

- `Left(List<SemanticError>)`: at least one critical semantic error.
- `Right(List<SemanticWarning>)`: no critical errors; warnings may be present.

Tests normally require no errors and no warnings unless a warning is the
expected result.

## 2. Setup Before Traversal

The ordinary analyzer test pipeline is:

```text
source -> Lexer -> AbstractSyntaxTreeBuilder -> ProgramContextCollector
       -> ProgramTypeResolver -> ProgramContextCollector -> SemanticAnalyzer
```

Template/typeclass integration tests use the full front half of the compiler:

```text
source -> Lexer -> AbstractSyntaxTreeBuilder -> ProgramContextCollector
       -> ProgramTypeResolver -> ProgramContextCollector -> TemplateInstantiator
       -> ProgramContextCollector -> ProgramTypeResolver -> ProgramContextCollector
       -> SemanticAnalyzer
```

`ProgramContextCollector` builds a `ProgramGlobalContext`, and
`ProgramTypeResolver` turns parser-level `TypeRef` values into context-owned
`TypeSign` instances before semantic analysis. The context records:

- primitive types `Int`, `Long`, `Double`, `String`, `Boolean`, and `Unit`;
- user-declared types from `ProgramRoot.types`;
- declared functions;
- typeclasses;
- instances;
- extern C function metadata whose signatures are resolved through the same
  context registry.

The root compiler parses root-level `stdlib.bred` and merges it before the user
program before this pipeline starts. The semantic analyzer therefore sees bred
stdlib declarations as ordinary declarations that happen to appear first.

`SemanticAnalyzer` registers `globalContext.externFunctionSignatures` before analyzing
the program, so externs used by semantic analysis and template instantiation
come from the same registry.

The analyzer expects a resolved AST. Running it directly on parser output can
produce false mismatches such as `TypeRef("String")` versus the context-owned
`String` type. Active compiler entry points run `ProgramTypeResolver` before
semantic analysis.

The analyzer then:

1. visits global variables;
2. registers user function signatures;
3. visits the program's functions.

Typeclass and instance declarations are not visited by the semantic analyzer.
They are accepted by the parser and are mainly consumed by template
instantiation.

## 3. Scope Model

The analyzer uses an internal recursive `Scope`.

Each scope contains:

- variable bindings: `name -> (TypeSign, isMutable)`;
- a shared identity table for expression types:
  `IdentityHashMap<ExpressionASTNode, TypeSign>`;
- registered function signatures;
- the expected return type for the current function;
- a flag controlling local control-flow checks.

Variable lookup walks parent scopes. Function lookup walks parent scopes and
matches exact name plus exact argument type list.

Array variables are represented by context-owned `Array<T>` `TypeSign` values
created with `ProgramGlobalContext.arrayOf(elementType)`. Consumers should use
`type.isArray`, `type.elementTypeOrNull`, and `type.arguments` instead of
matching the raw type name.

Array parameters are converted to the same representation during function
signature registration and when function arguments are put into scope. Top-level
static array declarations (`val name: T[n]`) are also bound as `Array<T>`; their
initializer, when present, is checked for exact size and element type.

## 4. Diagnostics

`SemanticErrorType` currently contains:

```text
VARIABLE_OVERSHADOW
VARIABLE_REDECLARATION
UNKNOWN_VARIABLE
VARIABLE_CHANGING_IMMUTABLE
UNEXPECTED_LVALUE
FUNCTION_NOT_FOUND
FUNCTION_IS_USED_AS_VARIABLE
REDEFINE_FUNCTION
FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS
COULDNT_RESOLVE_ARGUMENT_TYPE
TYPE_CHECKER_INCOMPATIBLE_TYPES
TYPE_CHECKER_INCONSISTENT_ARRAY_TYPE
ARRAY_INDEX_IS_NOT_INTEGER
INVALID_AMOUNT_OF_ARGUMENTS_IN_ARRAYS_INITIALIZATION
ARRAY_IS_EXPECTED_BUT_GOT_SCALAR
BLOCK_CONTAINS_MORE_THAN_ONE_RETURN
BLOCK_CONTAINS_CODE_AFTER_RETURN
METHOD_HAS_NO_RETURN
METHOD_HAS_WRONG_RETURN
EXPLICIT_RETURN_IS_EXPECTED
```

Current emitted wrappers:

| Wrapper | Critical |
|---------|----------|
| `VariableScopeSemanticError` | yes |
| `FunctionSemanticError` | yes |
| `TypeSemanticError` | yes |
| `ControlFlowSemanticError` | value supplied at creation; current errors are critical |
| `SemanticWarning` | no |

Each wrapper can carry an optional `SemanticDiagnosticDetail`. The detail does
not change semantic behavior; it only gives the formatter enough context to
produce human-readable diagnostics. Existing wrapper constructors remain
compatible because the detail parameter has a default value.

`SemanticDiagnosticFormatter` is the shared user-facing formatter for the active
pipeline. `BredToCCompiler` and `TACCompilerImpl` use it when reporting semantic
failures. The formatter prefers exact detail data such as variable names,
expected/actual types, call argument types, overload candidates, and array
sizes. If a diagnostic has no detail, it falls back to the error type plus an AST
node summary. Source positions are best-effort: variable and operator based
nodes use existing token/operator positions, while statement-level nodes without
stored spans are described by subject.

Example formatted diagnostics:

```text
Semantic error UNKNOWN_VARIABLE at 2:18: unknown variable 'missing'.
Semantic error TYPE_CHECKER_INCOMPATIBLE_TYPES: cannot initialize 'n: Int': expected Int, got String.
Semantic error FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS: no overload of 'takeInt' accepts (String); candidates: takeInt(Int): Unit.
```

`VARIABLE_OVERSHADOW` is emitted as a warning, not as a critical error.

Some enum values exist but are not covered by current tests or are not normally
emitted by the current analyzer, for example `FUNCTION_IS_USED_AS_VARIABLE` and
`METHOD_HAS_NO_RETURN`.

## 5. Variables

### 5.1 Declarations

For scalar local declarations:

1. Check whether the name exists in the current or parent scope.
2. If it exists in the current scope, emit `VARIABLE_REDECLARATION`.
3. If it exists only in a parent scope, emit warning `VARIABLE_OVERSHADOW`.
4. Hide the declared name while analyzing the initializer, so self-reference
   reports `UNKNOWN_VARIABLE`.
5. Analyze the initializer expression.
6. Require the declared type to equal the initializer expression type.
7. Put the declared name in scope.

The same redeclaration and shadowing policy is used for global scalar
variables.

### 5.2 Assignment

Assignments require a parser-level left value:

- variable expression;
- array element access.

The analyzer additionally:

- analyzes the left side;
- analyzes the right side;
- rejects assignment to immutable scalar variables with
  `VARIABLE_CHANGING_IMMUTABLE`;
- requires left and right types to be equal.

Array element assignment is allowed even when the array binding was declared
with `val`; mutability is checked only for scalar variable lvalues.

## 6. Functions

### 6.1 Registration

Before function bodies are analyzed, user functions are registered after global
variables are processed.

A user function signature is:

```text
name + argument `TypeSign` list from the active `ProgramGlobalContext`
```

Return type and parameter names are not part of duplicate detection. Therefore
same name and same argument types with a different return type is
`REDEFINE_FUNCTION`.

Array parameters are registered as `Array<elementType>`:

```text
fun take(values: Int[])
```

is registered as:

```kotlin
FunctionSignature("take", listOf(context.arrayOf(context.intType)), ...)
```

### 6.2 Calls

For a function call:

1. Analyze all argument expressions.
2. Read each argument type from the expression type table.
3. Resolve a function by exact name and exact positional argument types.
4. If the name exists but no signature matches, emit
   `FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS`.
5. If no function with that name exists, emit `FUNCTION_NOT_FOUND`.
6. Store the resolved result type on the call expression.

Forward function references are valid because all user functions are registered
before bodies are visited.

Function names are not first-class values. A bare function name in expression
position is analyzed as a variable and normally produces `UNKNOWN_VARIABLE`.

## 7. Expression Type Rules

Literal types:

| Expression node | Type |
|-----------------|------|
| `IntLiteralExpressionASTNode` | `Int` |
| `LongLiteralExpressionASTNode` | `Long` |
| `DoubleLiteralExpressionASTNode` | `Double` |
| `StringLiteralExpressionASTNode` | `String` |
| `BooleanLiteralExpressionASTNode` | `Boolean` |

Variables read their type from scope. Function calls read their result type
from the resolved signature.

### 7.1 Unary Operators

`TypeValidator.produceUnaryType` defines:

| Operator | Operand type | Result |
|----------|--------------|--------|
| `-` | `Int` | `Int` |
| `-` | `Long` | `Long` |
| `-` | `Double` | `Double` |
| `!` | `Boolean` | `Boolean` |

Other operands produce `TYPE_CHECKER_INCOMPATIBLE_TYPES`.

The analyzer stores the produced type on the unary expression node.

### 7.2 Binary Operators

`TypeValidator.produceBinaryType` defines:

| Operators | Accepted operands | Result |
|-----------|-------------------|--------|
| `==`, `!=` | equal types | `Boolean` |
| `+` | `Int`/`Int`, `Long`/`Long`, `Double`/`Double`, `String`/`String` | operand type |
| `-`, `*`, `/` | `Int`/`Int`, `Long`/`Long`, `Double`/`Double` | operand type |
| `%` | `Int`/`Int`, `Long`/`Long` | operand type |
| `&&`, `||` | `Boolean` and `Boolean` | `Boolean` |
| `<`, `>`, `<=`, `>=` | numeric/numeric or `String`/`String` | `Boolean` |

There is no numeric promotion for arithmetic. `Int + Double`, `Int + Long`,
and `Long + Double` are rejected.

Note: the implementation of relational operators accepts mixed
`Int`/`Long`/`Double` comparisons because it checks that both operands are
numeric, not that they are equal. This is tracked in `docs/TODO.md`.

## 8. Arrays

### 8.1 Local Arrays

For a local static array declaration:

```bred
val values: Int[3] = [1, 2, 3]
```

the analyzer:

1. analyzes the initializer list when present;
2. checks that initializer length equals declaration size;
3. checks that the initializer element type equals the declared element type;
4. puts the variable as `Array<Int>` in scope.

`val values: Int[3]` without initializer is valid and puts `Array<Int>` in
scope.

Empty initializer lists are valid only when the declared size is `0`.

### 8.2 Global Static Arrays

Top-level immutable static arrays use the same scope representation as local
arrays:

```bred
val values: Int[3] = [1, 2, 3]
```

The analyzer binds `values` as `Array<Int>`, so `values[0]` and calls expecting
`Int[]` work from function bodies. Global array initializers are checked for
exact size, homogeneous element type, and declared element compatibility.
The array name is hidden while its own initializer is analyzed, so
`val values: Int[1] = [values[0]]` reports `UNKNOWN_VARIABLE`.

### 8.3 Array Initialization Expressions

Each element is analyzed. If the set of element types has more than one member,
the analyzer emits `TYPE_CHECKER_INCONSISTENT_ARRAY_TYPE`.

For a non-empty homogeneous list, the expression type is the element type.
For an empty list, no element type is stored; size checking still happens at
the declaration node.

### 8.4 Array Access

For `values[index]`, the analyzer:

1. resolves `values`;
2. requires `type.isArray`;
3. analyzes `index`;
4. requires the index type to be `Int`;
5. stores `type.elementTypeOrNull` on the access expression.

Using array access on a scalar variable produces `ARRAY_IS_EXPECTED_BUT_GOT_SCALAR`.

## 9. Control Flow and Returns

The parser appends a synthetic non-explicit Unit return to functions with no
top-level explicit return. The analyzer ignores non-explicit synthetic returns
for control-flow guarantees.

Return expression checking:

- explicit `return` with no expression has type `Unit`;
- explicit `return expression` uses the expression type;
- the actual return type must equal the current function's expected type;
- mismatches produce `METHOD_HAS_WRONG_RETURN`.

Non-`Unit` functions must have a guaranteed explicit return. A block guarantees
return if it contains:

- an explicit return statement; or
- an `if` statement with an `else` branch where both branches guarantee return.

`while` and `for` bodies do not satisfy the function-level return requirement.

Block flow checks ignore synthetic returns and detect:

- `BLOCK_CONTAINS_CODE_AFTER_RETURN` when ordinary code follows a guaranteed
  return;
- `BLOCK_CONTAINS_MORE_THAN_ONE_RETURN` when another explicit return appears
  after a guaranteed return.

For-loop desugared content is visited with control-flow checks disabled for
that synthetic block, but nested statements are still semantically analyzed.

## 10. Typeclasses, Instances, and Templates

The semantic analyzer does not currently validate typeclass declarations or
instance declarations directly.

Template and typeclass method rewriting happens in
`parser/src/main/kotlin/org/nnezh/bred/codegenerator/TemplateInstantiator.kt`.
This component:

- collects generic functions as templates;
- collects typeclass method names;
- clones instance methods using mangled names;
- instantiates generic function calls by structurally matching parameter types
  against argument types, including `Array<T>` and scalar `T` in the same call;
- normalizes inferred concrete types through `ProgramGlobalContext` before
  comparing mappings;
- replaces generic parameter types in generated function arguments, return
  types, local scalar declarations, and local static array declarations;
- rewrites typeclass method calls to mangled instance method calls when an
  instance is available;
- returns `ASTError` on missing constrained instances, missing instance
  methods, or generated name collisions.

Because this is outside the current analyzer traversal, semantic validation of
raw typeclass and instance declarations remains incomplete.

## 11. Standard Library and Extern Functions

Root compilation implicitly prepends declarations from `stdlib.bred` before
the user program. These declarations are parsed, resolved, instantiated,
checked, lowered, and rendered like user code.

`common.ExternalFunctionSpecs` defines C runtime extern specs as `TypeRef`
values. The active `ProgramGlobalContext` resolves them to semantic signatures
for:

```text
println(String): Unit
stringToInt(String): Int
doubleToString(Double, String, Int): Unit
stringToDouble(String): Double
intToDouble(Int): Double
readInt(): Int
readDouble(): Double
readBoolean(): Boolean
doubleToInt(Double): Int
booleanToString(Boolean, String, Int): Unit
stringEquals(String, String): Boolean
readString(): String
intToString(Int): String
longToString(Long): String
doubleToString(Double): String
random(Int, Int): Int
currentTimeMillis(): Long
```

The semantic tests cover representative externs such as `println`,
`readInt`, `stringEquals`, `longToString`, and `currentTimeMillis`.

## 12. Known Gaps

The current implementation and tests leave several open items. Actionable
entries are maintained in `docs/TODO.md`.

Important known gaps:

- semantic validation for typeclasses and instances is incomplete;
- `mut` is lexed but unused by the parser;
- mixed numeric comparison behavior is still a design decision;
- global initializers cannot call user functions;
- empty array initializer expressions still do not carry their own element
  type outside declaration context.


