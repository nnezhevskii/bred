# bred — Grammar / Parser TODO

Follow-ups for lexer, AST, parsers, tests, and docs. Full inconsistency table: [`grammar.md`](grammar.md) §10.

Item IDs (G-02, G-26, …) are stable references; sections below group by **when to act**, not by subsystem.

---

## Now — parser stage (high ROI)

| ID | Item | Current state | Action |
|----|------|---------------|--------|
| G-26 | Type error message asymmetry | Params/`val`: `Invalid type …` via `parseOrNull`; return type: `Unexpected type …` via `fromString` | Unify messages and/or one type resolver API |
| G-02 | `var` declaration | Keyword lexed; `MutableVariableInitializationASTNode` only from `ForParser` desugar (no surface `var`) | **Decide:** add `MutableInitializationParser` (mirror `val`) **or** document `var` as intentionally unsupported and stop implying it in docs/examples |
| G-21 | Semicolon between statements | `;` token lexed, never consumed; behavior undocumented | One test: `{ val a: Int = 1; val b: Int = 2 }` — assert ignore vs error |
| G-22 | Non-identifier call statement | `StatementParser` routes only `identifier '('`; §10 #8 | One test: `(f)()` at statement level — assert `Left` |

**Suggested order:** G-26 → G-02 (decision) → G-21, G-22.

---

## Later — semantic analysis & typechecker

Requires a phase after AST construction (not parser-only).

| ID | Item | Current state | Action |
|----|------|---------------|--------|
| G-31 | Missing return on non-Unit functions | `FunctionParser` appends synthetic `return Unit` even for `: Int` without return (`add`, `compute` in `ai_generated.bred`) | Report error when `resultType != Unit` and no explicit return; treat synthetic Unit on non-Unit as invalid |
| G-09 | For-loop bound types | Desugar hardcodes `Int` counter; bounds are any expression syntactically | Type-check bounds (or document runtime-only behavior) when analysis exists |
| G-03 | Type inference for `val` | `val z = expr` rejected; `: Type` required | Only with inference / typechecker — or keep explicit types as permanent design |

---

## Later — language expansion (nice when needed)

Not blocking parser completeness; implement when the language actually needs them.

| ID | Item | Current state | Action |
|----|------|---------------|--------|
| G-05 | `else if` chains | Only `else block`; no `else if` desugar | Desugar to nested `if` or keep unsupported (+ test G-25) |
| G-06 | Statement separators (`;`) | Token in lexer, unused in grammar | Implement terminators **or** remove `Semicolon` from lexer |
| G-12 | Member access (`obj.field`) | `.` token exists; not in expression parser | Implement or remove dot from lexer |
| G-13 | Indirect calls `(f)()` | Call suffix only on identifier primary | Implement if call grammar should allow it |

---

## Nice to have — no urgent action

| ID | Item | Notes |
|----|------|-------|
| G-14 | Call statement routing | Not a bug: `StatementParser` intentionally routes `identifier '('`. `CallStatementParser` accepting any expression is internal flexibility. Document in grammar if behavior should stay narrow. |
| G-25 | `else if` test | Tied to G-05 — assert failure until implemented |
| G-16 | Package split | `org.nnezh.org.nnezh.ast` vs `org.nnezh.ast` — refactor when convenient |

---

## Priority snapshot

| When | IDs |
|------|-----|
| **Now** | G-26, G-02, G-21, G-22 |
| **Next pipeline** | G-31, G-09, G-03 |
| **When language grows** | G-05, G-06, G-12, G-13 |
| **Optional** | G-14, G-16, G-25 |
