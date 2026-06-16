# bred — Grammar / Parser TODO

Actionable follow-ups derived from gaps between lexer, AST, parsers, tests, and examples. See [`grammar.md`](grammar.md) §10 for the full inconsistency table.

---

## Language surface — missing parsers

| ID | Item | Current state | Suggested action |
|----|------|---------------|------------------|
| G-01 | `return` statement | Keyword lexed; no AST node, no parser (`max.bred` uses it) | Add `ReturnStatementASTNode`, `ReturnParser`, wire into `StatementParser`; decide whether value is required |
| G-02 | `var` declaration | Keyword lexed; `MutableVariableInitializationASTNode` exists only from for-desugar | Add `MutableInitializationParser` (mirror `val`) or document that `var` is intentionally unsupported |
| G-03 | Type inference for `val` | `val z = expr` rejected; required in `ImmutableInitializationParser` | Either implement inference or update examples (`max.bred` L20) |
| G-04 | Optional function return type | `fun main() { }` rejected; `FunctionParser` requires `: returnTypeName` | Decide if return type should be optional (default `Unit`?) and implement |
| G-05 | `else if` chains | Not implemented; only `else block` | Implement desugaring to nested `if` or document as unsupported |
| G-06 | Statement separators (`;`) | Token lexed, never consumed | Either implement `;` as statement terminator or remove token from lexer |

---

## Type system — parser inconsistencies

| ID | Item | Current state | Suggested action |
|----|------|---------------|------------------|
| G-07 | `Unit` return type | Used in examples; not in `Type` enum | Add `UnitType` to `Type` or document as opaque string only |
| G-08 | Return type validation | Params/`val` use `Type.parseOrNull`; return type stored as raw `String` | Validate return type the same way as param types, or document intentional asymmetry |
| G-09 | For-loop bound types | Desugar always uses `Int` counter; bounds are any expression | Add type checks or document runtime-only behavior |

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
| G-15 | `StatementParser` bounds check | Lone identifier without EOF → `IndexOutOfBoundsException` | Guard `top(1)` or require EOF; return `ASTError` (`StatementParserTest` documents this) |
| G-16 | Package split | Parsers in `org.nnezh.org.nnezh.ast`, nodes in `org.nnezh.ast` | Consolidate packages when convenient |

---

## Examples and documentation

| ID | Item | Current state | Suggested action |
|----|------|---------------|------------------|
| G-17 | `examples/simple.bred` | Contains constructs invalid for current parser | Rewrite as valid reference program or mark as negative/fixture file |
| G-18 | `examples/max.bred` | `return`, untyped `val`, `fun main()` without return type | Fix or split into valid/invalid sections |
| G-19 | `examples/sandbox.bred` | `fun main() { }` missing `: Type` | Add `: Unit` or other valid return type |
| G-20 | End-to-end parse test | No example file parses fully today | Add `examples/valid.bred` and integration test through `AbstractSyntaxTreeBuilder` |

---

## Test coverage gaps

| ID | Item | Suggested test |
|----|------|----------------|
| G-21 | Semicolon between statements | `parseFromSource("{ val a: Int = 1; val b: Int = 2 }")` — assert behavior |
| G-22 | Non-identifier call statement | `parseFromSource("(f)()")` at statement level — assert `Left` |
| G-23 | Lexer keywords `for`, `in`, `to` | Extend `LexerTest.keywords are recognized` |
| G-24 | Invalid return type string | `fun f(): Foo { }` — document accepted; add test |
| G-25 | `else if` | Assert parse failure or implement |

---

## Priority suggestion

1. **G-15** — bug fix (bounds check)
2. **G-17–G-20** — examples + end-to-end test (documentation hygiene)
3. **G-01, G-04** — align with `max.bred` / `sandbox.bred` intent
4. **G-07, G-08** — type system consistency
5. **G-10** — syntax alignment (`if`/`while`)
