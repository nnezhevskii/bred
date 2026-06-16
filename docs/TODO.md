# bred — Grammar / Parser TODO

Actionable follow-ups derived from gaps between lexer, AST, parsers, tests, and examples. See [`grammar.md`](grammar.md) §10 for the full inconsistency table.

---

## Language surface — missing parsers

| ID | Item | Current state | Suggested action |
|----|------|---------------|------------------|
| G-01 | `return` statement | Keyword lexed; no AST node, no parser | Add `ReturnStatementASTNode`, `ReturnParser`, wire into `StatementParser`; decide whether value is required |
| G-02 | `var` declaration | Keyword lexed; `MutableVariableInitializationASTNode` exists only from for-desugar | Add `MutableInitializationParser` (mirror `val`) or document that `var` is intentionally unsupported |
| G-03 | Type inference for `val` | `val z = expr` rejected; required in `ImmutableInitializationParser` | Either implement inference or keep explicit `: Type` as the only form |
| G-05 | `else if` chains | Not implemented; only `else block` | Implement desugaring to nested `if` or document as unsupported |
| G-06 | Statement separators (`;`) | Token lexed, never consumed | Either implement `;` as statement terminator or remove token from lexer |

---

## Type system — parser inconsistencies

| ID | Item | Current state | Suggested action |
|----|------|---------------|------------------|
| G-09 | For-loop bound types | Desugar always uses `Int` counter; bounds are any expression | Add type checks or document runtime-only behavior |
| G-26 | Type error message asymmetry | Params/`val`: `Invalid type …`; return type: `Unexpected type …` | Unify messages and/or use one resolver (`parseOrNull` vs `fromString`) |
| G-27 | `Unit` as parameter type | `Unit` is in `Type` enum; grammar text historically excluded it from params | Document whether `fun f(u: Unit)` is intentional; add test if yes |

---

## Control-flow syntax asymmetry

| ID | Item | Current state | Suggested action |
|----|------|---------------|------------------|
| G-10 | `if` vs `while` parentheses | `if (expr)` required; `while expr` does not require parens | Align syntax (require parens for both or neither) or document as intentional |
| G-11 | For-loop surface `var` | Desugar emits `MutableVariableInitializationASTNode` without surface `var` | Expose `var` syntax or rename AST node to reflect internal-only use |

---

## Expression / call limitations

| ID | Item | Current state | Suggested action |
|----|------|---------------|------------------|
| G-12 | Member access (`obj.field`) | `.` token exists; not used in expression parser | Implement or remove `Punctuation.Dot` from lexer if unused |
| G-13 | Indirect calls `(f)()` | Call suffix only on identifier primary | Implement if desired |
| G-14 | Call statement routing | `StatementParser` only routes `identifier '('`; `CallStatementParser` wraps any expression | Align routing with intended statement grammar |

---

## Implementation bugs

| ID | Item | Current state | Suggested action |
|----|------|---------------|------------------|
| G-16 | Package split | Parsers in `org.nnezh.org.nnezh.ast`, nodes in `org.nnezh.ast` | Consolidate packages when convenient |

---

## Test coverage gaps

| ID | Item | Suggested test |
|----|------|----------------|
| G-21 | Semicolon between statements | `parseFromSource("{ val a: Int = 1; val b: Int = 2 }")` — assert behavior |
| G-22 | Non-identifier call statement | `parseFromSource("(f)()")` at statement level — assert `Left` |
| G-23 | Lexer keywords `for`, `in`, `to` | Extend `LexerTest.keywords are recognized` |
| G-25 | `else if` | Assert parse failure or implement |

---

## Priority suggestion

1. **G-01** — `return` statement
2. **G-10** — syntax alignment (`if`/`while`)
3. **G-26** — unify type error messages / resolver API
4. **G-21–G-25** — remaining test coverage gaps
