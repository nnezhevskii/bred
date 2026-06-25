# bred

Kotlin compiler for the **bred** language (`.bred` files): lexer → AST → semantic analysis → LLTAC (low-level three-address code).

## Pipeline

```
.bred source → Lexer → ParserFactory / AST → SemanticAnalyzer → LLTACGenerator → PrettyPrinter
```

| Stage | Package | Entry |
|-------|---------|-------|
| Lexer | `org.nnezh.lexer` | `Lexer.tokenize()` |
| AST | `org.nnezh.ast`, `org.nnezh.org.nnezh.ast.parsers` | `AbstractSyntaxTreeBuilder` |
| Semantic | `org.nnezh.org.nnezh.semantic` | `SemanticAnalyzer` (4 passes) |
| Codegen | `org.nnezh.ICGenerator` | `LLTACGenerator`, `TACCompiler` |
| CLI | — | `Main.kt` |

## Build and test

Requires **JDK 25** (project uses `azul-25.0.3` locally):

```powershell
$env:JAVA_HOME = "C:\Users\Nikita\.jdks\azul-25.0.3"
.\gradlew test
```

Stack: Kotlin 2.3, Gradle, JUnit 5, arrow-kt, Kover.

## Run

Default input is `examples/3ac.bred` (arrays, functions, control flow):

```powershell
.\gradlew run
```

Or run `Main.kt` from the IDE with an optional path argument.

## Language status

**Implemented:** functions, `val`/`var`, `if`/`while`/`for`, assignments, calls, operators, built-in `println`, **static arrays** (`val arr: Int[n]`, `arr[i]`, `fun f(a: Int[])`).

**Not implemented:** global `var`, `else if`, member access, generics, user-defined types, type inference for `val x = expr`.

Known gaps and follow-ups: [`docs/TODO.md`](docs/TODO.md). Failing tests in `TypeCheckerTest` region **Arrays** track intended semantics vs current `TypeChecker` bugs.

## Documentation

| File | Contents |
|------|----------|
| [`docs/grammar.md`](docs/grammar.md) | Syntax reference (lexer, parsers, EBNF) |
| [`docs/semantic_analysis_draft.md`](docs/semantic_analysis_draft.md) | Semantic passes, errors, known gaps |
| [`docs/TODO.md`](docs/TODO.md) | Actionable items (G-IDs) |
| [`skills/`](skills/) | Agent rules for tests, grammar maintenance, reviews |

**Canonical valid program:** [`examples/ai_generated.bred`](examples/ai_generated.bred) (`AiGeneratedProgramIntegrationTest`).

## Examples

| File | Role |
|------|------|
| `examples/ai_generated.bred` | Normative integration fixture |
| `examples/3ac.bred` | Array-heavy demo (default `Main` input) |
| `examples/simple.bred`, `max.bred`, `sandbox.bred` | Ad-hoc scratch (non-normative) |

## Tests

- **Unit:** parsers, lexer, analyzers under `src/test/kotlin/`
- **Integration:** `AiGeneratedProgramIntegrationTest`
- **Snapshots:** `LLTAGSnapshotTester` — `.bred` vs expected `.3ac` in `src/test/resources/`
