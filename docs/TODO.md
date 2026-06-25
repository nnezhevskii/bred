# bred — Grammar / Parser TODO

Follow-ups for lexer, AST, parsers, tests, docs, and semantic analysis. Full inconsistency table: [`grammar.md`](grammar.md) §10.

Item IDs (G-02, G-21, …) are stable references; sections below group by **when to act**, not by subsystem.

---

## Later — semantic analysis & typechecker

Requires a phase after AST construction (not parser-only).

| ID | Item | Current state | Action |
|----|------|---------------|--------|
| G-31 | Missing return on non-Unit functions | `FunctionParser` appends synthetic `return Unit` even for `: Int` without return (`add`, `compute` in `ai_generated.bred`) | Report error when `resultType != Unit` and no explicit return; treat synthetic Unit on non-Unit as invalid |
| G-09 | For-loop bound types | Desugar hardcodes `Int` counter; bounds are any expression syntactically | Type-check bounds (or document runtime-only behavior) when analysis exists |
| G-03 | Type inference for `val` | `val z = expr` rejected; `: Type` required | Only with inference / typechecker — or keep explicit types as permanent design |
| G-32 | Assignment to immutable `val` | `VariableScopeAnalyzer` checks mutability; `val` static arrays were parsed with `isMutable = true` (G-39) | Ensure `val` arrays use `isMutable = false`; add tests for `arr[i] = …` on `val arr` |
| G-33 | `REDEFINE_FUNCTION` early abort | `FunctionSubAnalyzer` returns immediately on duplicate name+arity; `where` is `ProgramASTNode`, bodies/globals not analyzed | Accumulate error and continue (or stop registration only); set `where` to duplicate `DeclareFunctionASTNode`; update `redefine stops entire program analysis early` test |
| G-34 | Function analyzer traversal policy | `FunctionSubAnalyzer` has no block short-circuit; differs from `VariableScopeSubAnalyzer` | Decide and document collect-all vs stop-after-critical; align implementation or document intentional difference |
| G-35 | Function parameter names vs registry | `analyzeFunctionArgumentASTNode` is a no-op; `fun f(println: Int)` is accepted | Decide whether parameter names must not collide with function registry; add check + test if required |
| G-37 | Static array init type check | `TypeChecker` compared `StaticArrayType` to init-list element type | **Fixed:** compare element types; see `TypeChecker.analyzeVariableInitializationASTNode` |
| G-38 | Scalar rhs on array declaration | `val arr: Int[3] = 42` threw `ClassCastException` | **Fixed:** reject with `ASTError` in `ImmutableInitializationParser` |
| G-39 | `val` static arrays `isMutable` | Parser set `isMutable = true` for `val` arrays | **Fixed:** `isMutable = false` in `ImmutableInitializationParser` |
| G-40 | Array assign index scope | `arr[i] = v` did not validate index variable | **Fixed:** `routeExpressionHandling` on index in `VariableScopeSubAnalyzer` |
| G-41 | `getResultType` overload match | Used set-containment `type in args` | **Fixed:** positional `signature.args == args` |

---

## Later — language expansion (nice when needed)

Not blocking parser completeness; implement when the language actually needs them.

| ID | Item | Current state | Action |
|----|------|---------------|--------|
| G-05 | `else if` chains | Only `else block`; no `else if` desugar | Desugar to nested `if` or keep unsupported (+ test G-25) |
| G-12 | Member access (`obj.field`) | `.` token exists; not in expression parser | Implement or remove dot from lexer |
| G-13 | Indirect calls `(f)()` | Call suffix only on identifier primary | Implement if call grammar should allow it |

---

## Nice to have — no urgent action

| ID | Item | Notes |
|----|------|-------|
| G-14 | Call statement routing | Not a bug: `StatementParser` intentionally routes `identifier '('`. `CallStatementParser` accepting any expression is internal flexibility. Document in grammar if behavior should stay narrow. |
| G-25 | `else if` test | Tied to G-05 — assert failure until implemented |
| G-16 | Package split | `org.nnezh.org.nnezh.ast` vs `org.nnezh.ast` — refactor when convenient (separate PR) |
| G-42 | `minMax.bred` snapshot | Fixture in `src/test/resources/` but not in `LLTAGSnapshotTester` | Register in snapshot suite when array IR is stable |

---

## Resolved / obsolete

| ID | Item | Resolution |
|----|------|------------|
| G-36 | `FunctionSubAnalyzer` not in pipeline | **Resolved:** `SemanticAnalyzer` runs `FunctionSubAnalyzer` as pass 2 |

---

## Priority snapshot

| When | IDs |
|------|-----|
| **Next pipeline** | G-31, G-09, G-03, G-32, G-33 |
| **When language grows** | G-05, G-12, G-13 |
| **Optional** | G-14, G-16, G-25, G-34, G-35, G-42 |
