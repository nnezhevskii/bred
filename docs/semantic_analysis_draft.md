# bred — Semantic Analysis (Draft)

This document specifies the **intended** semantic checks performed after AST construction. It is derived from `VariableScopeSubAnalyzer`, `FunctionSubAnalyzer`, their tests, and current design discussions.

**Source of truth for behavior:**
- variable scope: `src/test/kotlin/org/nnezh/semantic/VariableScopeAnalyzerTest.kt`
- function registry and calls: `src/test/kotlin/org/nnezh/semantic/FunctionAnalyzerTest.kt`

## Scope

This phase runs **after** parsing/AST building and is **not** part of the grammar (`docs/grammar.md` covers syntax only).

Current focus:
- name resolution and lexical scoping for variables (`VariableScopeSubAnalyzer`)
- detection of shadowing / redeclaration
- detection of unknown variables
- collecting **all** diagnostics found within a single statement (variable scope)
- stopping block traversal after a statement with a critical error (variable scope)
- function registry: built-ins + user declarations (`FunctionSubAnalyzer`)
- function call arity checking (name + argument count; types not checked)
- allowing function and variable names to coexist (syntactic disambiguation)
- forbidding duplicate function signatures (same name + same parameter types; return type excluded)

Out of scope for this draft:
- type checking and type inference (see `docs/TODO.md`, semantic analysis section)
- distinguishing self-use in initializer (`val a = a + 1`) from unknown variable — treated like Kotlin: reported as `UNKNOWN_VARIABLE`
- runtime semantics

## Inputs / Outputs

- **Input**: `ProgramASTNode` (root of AST).
- **Output**: list of semantic diagnostics (`SemanticError` sealed interface):
  - `VariableScopeSemanticError(where, critical, errorType)`
  - `FunctionSemanticError(where, critical, errorType)`
  - `SemanticErrorType` for variables: `UNKNOWN_VARIABLE`, `VARIABLE_REDECLARATION`, `VARIABLE_OVERSHADOW`
  - `SemanticErrorType` for functions: `FUNCTION_NOT_FOUND`, `FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS_AMOUNT`, `REDEFINE_FUNCTION`
  - `FUNCTION_IS_USED_AS_VARIABLE` exists in the enum but is **not emitted** (legacy / reserved)

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

## Test coverage map (variable scope)

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

## Function semantics (`FunctionSubAnalyzer`)

### Model: function registry

- **Registry**: `name → List<FunctionSignature>` (overloads by arity and parameter types).
- **Seeded** with built-ins from `BuiltInMethods` (`println`, `readInt`, `stringConcat`, etc.).
- **User functions** registered from `ProgramASTNode.functions` before any body analysis (forward references between functions are valid).

#### Signature identity (duplicate detection)

A function is uniquely identified for `REDEFINE_FUNCTION` by:

```ebnf
signatureKey ::= name '(' paramType { ',' paramType } ')' ;
```

- `paramType` — ordered list of parameter types (`FunctionSignature.args`).
- **Return type is not part of the key** — two declarations with the same name and parameter types but different return types are a duplicate.

Overload rules:

| Case | Allowed |
|------|---------|
| Same name, different arity | yes |
| Same name, same arity, different parameter types | yes |
| Same name, same parameter types, different return type | no → `REDEFINE_FUNCTION` |
| Same name, same parameter types, different parameter names only | no → `REDEFINE_FUNCTION` |

```bred
fun foo(x: Int): Unit { }
fun foo(x: String): Unit { }        // ok — parameter types differ

fun foo(x: String): Unit { }
fun foo(x: String): String {       // REDEFINE_FUNCTION — same parameter types, return type ignored for overload
    return x
}

fun foo(a: Int): Unit { }
fun foo(b: Int): Unit { }           // REDEFINE_FUNCTION — parameter types [Int] == [Int]
```

### Traversal policy (function analyzer)

**Differs from variable scope analyzer:**

- All function declarations are registered in a first pass; then `globalVariables` and function bodies are analyzed.
- **No short-circuit** within blocks: every statement is visited regardless of prior critical errors in the same block.
- On `REDEFINE_FUNCTION` during registration, analysis **stops entirely** (current implementation — see `G-33` in `docs/TODO.md`).
- Argument expressions of a call are always analyzed after the call-site check (even when arity is wrong).

### FUNCTION_NOT_FOUND (critical)

Emitted when a call references a name not present in the registry.

Properties:
- `isCriticalError = true`
- `where` = `FunctionCallExpressionNode`

```bred
fun main(): Unit {
    missing(1)   // FUNCTION_NOT_FOUND
}
```

### FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS_AMOUNT (critical)

Emitted when the name exists but no overload matches the call's argument count. Argument **types** are not checked.

Properties:
- `isCriticalError = true`
- `where` = `FunctionCallExpressionNode`

```bred
fun moo(x: Int, y: Int): Unit { }
fun main(): Unit {
    moo(3, 2)           // ok
    moo("x", true)      // ok — arity matches
    moo(3)              // FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS_AMOUNT
    moo()               // FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS_AMOUNT
    moo(1, 2, 3)        // FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS_AMOUNT
}
```

### REDEFINE_FUNCTION (critical)

Emitted when a user function duplicates an existing signature (built-in or user): same **name + parameter types** (return type does not distinguish overloads).

Properties:
- `isCriticalError = true`
- `where` = `ProgramASTNode` (current implementation; should point to the duplicate `DeclareFunctionASTNode` — see `G-33`)

```bred
fun println(x: String): Unit { }   // REDEFINE_FUNCTION — builtin println(String) exists
fun foo(a: Int): Unit { }
fun foo(b: Int): Unit { }           // REDEFINE_FUNCTION — same parameter types [Int]
fun foo(x: String): Unit { }
fun foo(x: String): String {       // REDEFINE_FUNCTION — same parameter types [String]
    return x
}
```

### Function and variable name coexistence

A name may denote **both** a function and a variable (`val` / `var`) in the same program. `FunctionSubAnalyzer` does not reject declarations or uses on that basis.

Disambiguation is **syntactic**:

| Surface form | Resolved as | Checked by |
|--------------|-------------|------------|
| `ident(...)` | function call | `FunctionSubAnalyzer` (registry + arity) |
| `ident` in expression | variable reference | `VariableScopeSubAnalyzer` (scope lookup) |
| `val ident` / `var ident` | variable declaration | not checked by function analyzer |
| `ident = expr` | assignment to variable | not checked by function analyzer |

```bred
fun foo(): Unit { }
val foo: Int = 1
fun main(): Unit {
    val x: Int = foo + 1   // variable foo — scope analyzer
    foo()                  // function foo — function analyzer
    bar(foo)               // variable foo as argument — ok for function analyzer
}
```

`FUNCTION_IS_USED_AS_VARIABLE` is no longer produced; bare `ident` in an expression is never treated as “using a function as a variable” by `FunctionSubAnalyzer`.

### Positive cases (no errors)

- call with matching arity (any argument types at call site — type resolution deferred to future typechecker)
- overload by arity
- overload by parameter types (same arity, different param types)
- forward reference between functions
- built-in calls with correct arity
- nested calls, calls in assignment RHS, statement-level calls
- function and variable sharing the same name (global/local, call + reference, builtin name)

## Function test coverage map

| Behavior | Test name (abbrev.) |
|----------|---------------------|
| Valid arity | `valid call with matching arity` |
| Types not checked | `argument types are not checked` |
| Overload by arity | `overload by arity is allowed`, `different arity is not redefine` |
| Overload by parameter types | `overload by parameter types is allowed` |
| Redefine same param types | `duplicate user function same arity`, `duplicate same parameter types different return type is redefine`, `duplicate same parameter types implicit Unit and explicit return type is redefine`, `redefine builtin with same arity` |
| Forward reference | `forward reference between functions` |
| Builtin call | `builtin call is valid` |
| Call in RHS / nested / statement | `call in assignment RHS`, `nested call in arguments`, `statement-level call` |
| Unknown function | `unknown function is critical` |
| Wrong arity | `too few arguments`, `too many arguments`, `zero args when one required`, `builtin wrong arity` |
| Same name coexistence | `global val and function with same name`, `local val and function with same name`, `global val and builtin with same name`, `var assignment shares name with function`, `bare identifier in expression is not function analyzer concern`, `variable reference when both function and variable exist`, `function call when variable with same name exists`, `variable as call argument when name collides with function`, `call and variable use in same block` |
| Arity error + arg analysis | `wrong arity and unknown args in same call both reported` |
| Redefine early abort | `redefine stops entire program analysis early` |

## Open test gaps

See `docs/TODO.md`:
- `G-32` — assignment to immutable `val` (no test yet)
- `G-33` — `REDEFINE_FUNCTION` early return and `where` node
- `G-34` — traversal policy for `FunctionSubAnalyzer`
- `G-35` — function parameter names vs function registry
- `G-36` — wire `FunctionSubAnalyzer` into `SemanticAnalyzer` pipeline
