# bred — Semantic Analysis (Draft)

This document specifies the **intended** semantic checks performed after AST construction. It is derived from `VariableScopeAnalyzer`, `VariableScopeAnalyzerTest`, and current design discussions.

**Source of truth for behavior:** tests in `src/test/kotlin/org/nnezh/semantic/VariableScopeAnalyzerTest.kt`.

## Scope

This phase runs **after** parsing/AST building and is **not** part of the grammar (`docs/grammar.md` covers syntax only).

Current focus:
- name resolution and lexical scoping for variables
- detection of shadowing / redeclaration
- detection of unknown variables
- collecting **all** diagnostics found within a single statement
- stopping block traversal after a statement with a critical error

Out of scope for this draft:
- type checking and type inference (see `docs/TODO.md`, semantic analysis section)
- distinguishing self-use in initializer (`val a = a + 1`) from unknown variable — treated like Kotlin: reported as `UNKNOWN_VARIABLE`
- runtime semantics

## Inputs / Outputs

- **Input**: `ProgramASTNode` (root of AST).
- **Output**: list of semantic diagnostics:
  - `VariableScopeError(where: ASTNode, isCriticalError: Boolean, errorType: VariableScopeErrorType)`
  - `VariableScopeErrorType ∈ { UNKNOWN_VARIABLE, REDECLARATION, OVERSHADOW }`

## Traversal policy

### Within one statement

The analyzer must return **all errors found while analyzing a single statement**, not only the first one.

Examples:
- initializer with multiple unknown names → one `UNKNOWN_VARIABLE` per unknown identifier
- shadowing declaration with invalid initializer → `OVERSHADOW` **and** all `UNKNOWN_VARIABLE` errors from the initializer expression

```bred
val globalSeedValue: Int = 5
fun foo(): Unit {
    val globalSeedValue: Int = leftOperand + rightOperand + missingDependency
}
```

Expected: 4 errors — `OVERSHADOW` for `globalSeedValue` plus `UNKNOWN_VARIABLE` for `leftOperand`, `rightOperand`, `missingDependency`.

### Within a block

Statements in a block are analyzed **sequentially**. If a statement produces at least one **critical** error, analysis of **later statements in the same block stops**.

```bred
fun foo(): Unit {
    val x: Int = globalSeedValue + 1          // ok
    val globalSeedValue: Int = leftOperand + … // critical errors here
    val unusedResult: Int = anotherMissing + x // must not be analyzed
}
```

### Within control-flow branches

- `if`: if the **condition** has a critical error, `then` and `else` blocks are not analyzed.
- `while`: if the **condition** has a critical error, the body block is not analyzed.

### Declaration binding

A variable is added to the current scope **only if** its initialization statement produced **no critical errors**.

## Model: Scopes and symbol tables

### Definitions

- **Scope**: symbol table `name → VariableDeclaration` with metadata:
  - `name: String`
  - `type: Type`
  - `isMutable: Boolean` (`true` for `var`, `false` for `val` and function arguments)
- **Lookup** (`lookUp`): returns `Pair<VariableDeclaration, isCurrentScope>`:
  - `second = true` — name declared in the **current** scope table
  - `second = false` — name found in a **parent** scope

### Where scopes are created

Nested lexical scopes:

| Scope | Created for | Parent |
|-------|-------------|--------|
| Global | top-level `val` declarations | — |
| Function | each `DeclareFunctionASTNode` | global |
| Block | each `BlockASTNode` (function body, `if` branches, `while` body, `for` desugared content) | surrounding scope |

### Visibility rules

- Names from outer scopes are visible in inner scopes.
- Names declared in an inner scope are **not** visible outside that scope.

```bred
fun main(): Unit {
    if (true) {
        val a: Int = 1
    }
    return a // UNKNOWN_VARIABLE
}
```

```bred
fun main(): Unit {
    for (i in 0 to 10) {
        println(i) // ok
    }
    println(i) // UNKNOWN_VARIABLE
}
```

## Rules: Variable diagnostics

### UNKNOWN_VARIABLE (critical)

Emitted when a variable name is used but cannot be resolved:

- `VariableExpressionNode` in expressions, initializers, call arguments, `return` values
- `AssignmentStatementASTNode` when the assignment target is unknown

The analyzer checks **both** the assignment target and the right-hand side expression.

Properties:
- `isCriticalError = true`
- `where` points to the offending `VariableExpressionNode`, or to `AssignmentStatementASTNode` for unknown assignment targets

Multiple unknown names in one expression produce **multiple** errors.

```bred
fun calc(radius: Double): Int {
    var circumferenceLength: Double = 2 * pi * radius
    circumferenceLength = 3 * length + tail - radius
}
```

Expected: `UNKNOWN_VARIABLE` for `length` and `tail`; `radius` and `circumferenceLength` are valid.

### REDECLARATION (critical)

Emitted when a name is declared twice in the **same** scope:

```bred
fun main(): Unit {
    val a: Int = 1
    val a: Int = 2 // REDECLARATION
}
```

Properties:
- `isCriticalError = true`
- `where` = second `VariableInitializationASTNode`
- distinguished from `OVERSHADOW` via `lookUp(...).second == true`

### OVERSHADOW (critical; shadowing is forbidden)

Declaring a name that already exists in a **parent** scope is forbidden.

Cases:
- local `val`/`var` shadowing a global constant
- function argument shadowing a global constant
- local declaration in a nested block shadowing an outer local

```bred
val Pi: Double = 3.14
fun calc(Pi: Double): Unit { } // OVERSHADOW on argument
```

```bred
val Pi: Double = 3.14
fun main(): Unit {
    val Pi: Double = 4.0 // OVERSHADOW
}
```

Properties:
- `isCriticalError = true`
- `where` = shadowing `VariableInitializationASTNode` or `FunctionArgumentASTNode`
- initializer expression is still analyzed; additional errors from the initializer are included in the result

## Positive cases (no errors)

- global constant visible inside functions
- function parameters visible in the function body
- outer-scope variables visible in nested blocks
- `var` initialized from globals and parameters
- later statements may use variables declared in earlier statements in the same scope

```bred
val pi: Double = 3.14
fun calc(radius: Double): Int {
    var circumferenceLength: Double = 2 * pi * radius
}
```

## Mutability rules (planned)

### Assignment to immutable `val` (TODO)

Assigning to an immutable variable must be rejected:

```bred
val Pi: Double = 3.14
fun some_method(): Unit {
    Pi = 4.0
}
```

Status:
- Not yet represented in `VariableScopeErrorType`
- Tracked as `G-32` in `docs/TODO.md`
- No test in `VariableScopeAnalyzerTest` yet

## Test coverage map

| Behavior | Test name (abbrev.) |
|----------|---------------------|
| Unknown in `return` | `unknown variable in expression is critical` |
| Unknown assignment target | `unknown variable in assignment is critical` |
| Unknown in initializer | `unknown variable in initializer is unknown variable not uninitialized` |
| Multiple unknowns in initializer | `unknown variables in initializer are all reported as unknown` |
| OVERSHADOW + unknowns in one statement | `returns all errors found inside statement but stops after critical statement` |
| Valid `var` init with global + param | `mutable variable initializer can use global constant and parameter` |
| Multiple unknowns in assignment RHS | `assignment reports all unknown variables in expression` |
| Stop after critical statement | `calc function reports only unknown identifiers in invalid reassignment` |
| Unknowns in call arguments | `function call reports all unknown variables in arguments` |
| `var` reassignment | `mutable variable can be reassigned after initialization` |
| `if` condition short-circuit | `critical error in if condition prevents analyzing then block` |
| REDECLARATION | `redeclaring name in same scope is critical` |
| OVERSHADOW in nested block | `shadowing variable in nested block is critical` |
| OVERSHADOW function arg | `shadowing function argument over global is critical` |
| OVERSHADOW local over global | `shadowing local variable over global is critical` |
| Outer scope visible inside block | `variable from outer scope is visible inside nested block` |
| Inner scope not visible outside | `variable declared in if/while block is not visible outside`, `for counter variable is not visible after loop` |

## Open test gaps

See `docs/TODO.md`: `G-32` (assignment to immutable `val` — no test yet).
