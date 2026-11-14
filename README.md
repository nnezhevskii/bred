# bred

Kotlin compiler for the **bred** language (`.bred` files): lexer -> AST -> semantic analysis -> LLTAC (low-level three-address code) -> C -> `.exe`.

## Pipeline

```text
.bred source + implicit stdlib.bred -> Lexer -> Parser / AST -> TemplateInstantiator -> SemanticAnalyzer -> LLTACGenerator -> CTranspile -> runtime.c + MSVC cl.exe
```

| Stage | Package | Entry |
|-------|---------|-------|
| Lexer | `org.nnezh.lexer` | `Lexer.tokenize()` |
| AST | `org.nnezh.bred.ast` | `AbstractSyntaxTreeBuilder` |
| Semantic | `org.nnezh` | `SemanticAnalyzer` |
| TAC | `org.nnezh.org.nnezh.ICGenerator` | `LLTACGenerator`, `TACCompilerImpl` |
| C backend | `org.nnezh.org.nnezh.compiler` | `CTranspile` |
| Standard library/runtime wiring | `org.nnezh` | `StandardLibraryProvider` |
| Root compile API | `org.nnezh` | `BredToCCompilerFactory`, `CExecutableBuilder` |
| CLI | `org.nnezh` | `Main.kt` |

## Build and test

Requires **JDK 25**. The project currently uses `azul-25.0.3` locally.

```powershell
$env:JAVA_HOME = "C:\Users\Nikita\.jdks\azul-25.0.3"
.\gradlew test
```

Stack: Kotlin 2.3, Gradle, JUnit 5, arrow-kt, Kover.

Documented follow-ups live in `docs/TODO.md`. A green module smoke test is:

```powershell
.\gradlew :parser:test :analyzer:test :tac:test :c-backend:test :test
```

## Run From CLI

Default input is `examples/3ac.bred`; default output is `main.exe`.

```powershell
.\gradlew run
```

CLI arguments:

```text
.\gradlew run --args="<source.bred> <output.exe> <keep-c>"
```

| Argument | Default | Meaning |
|----------|---------|---------|
| `source.bred` | `examples/3ac.bred` | Input `.bred` file |
| `output.exe` | `main.exe` | Executable to produce; `.exe` is added by the builder if missing |
| `keep-c` | `false` | `true` keeps the intermediate C file next to the output executable |

Example:

```powershell
.\gradlew run --args="examples/3ac.bred build/out/demo.exe true"
```

The CLI prints build messages, then launches the executable after the builder reports success. The program inherits console input and output, and the CLI waits for it to finish and returns its exit code. Compilation, build, and launch failures return a non-zero code.

Generated root-level artifacts such as `main.c`, `main.obj`, and `main.exe`
are ignored. If they were tracked in an older checkout, remove them from the
index once with `git rm --cached -- main.c main.obj`.

## MSVC Setup

The executable builder invokes MSVC through `vcvars64.bat`, then runs `cl.exe`.

Default `vcvars64.bat` path:

```text
C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvars64.bat
```

Override it with:

```powershell
$env:VCVARS64_PATH = "C:\Path\To\VC\Auxiliary\Build\vcvars64.bat"
```

The C compiler is invoked with `/Od`; C optimizations are intentionally disabled.

## Root Compile API

There are two independent root-level entities.

### `.bred` File -> C Lines

```kotlin
val compiler = BredToCCompilerFactory().create()
val result: Either<List<String>, List<String>> = compiler.compile("examples/3ac.bred")
```

Result shape:

- `Left(List<String>)`: source read errors, lexer errors, parser errors, template instantiation errors, semantic errors, or C generation errors.
- `Right(List<String>)`: generated C body lines, without `runtime.c` prepended.

Before type resolution, this layer parses root-level `stdlib.bred` and merges
its declarations before the user program. Stdlib read, lexer, or parser
failures are returned with a `Standard library error:` prefix.

This layer only compiles to C lines. It does not write files and does not call a C compiler.

### C Lines -> `.exe`

```kotlin
val cLines: List<String> = ...
val messages: List<String> = CExecutableBuilder().build(
    cLines = cLines,
    outputExe = "build/out/program.exe",
    keepIntermediateC = false,
)
```

What it does:

1. Reads `runtime.c` from the active `StandardLibraryProvider`.
2. Prepends the runtime to `cLines`.
3. Writes an intermediate `.c` file next to the output executable.
4. Runs MSVC `cl.exe` through `vcvars64.bat` with `/Od`.
5. Produces the requested `.exe`.
6. Deletes the intermediate `.c` file unless `keepIntermediateC = true`.
7. Returns compiler output and I/O/build messages as `List<String>`.

The builder reports ordinary build problems in the returned message list: missing `runtime.c`, missing `vcvars64.bat`, compiler failures, interrupted compiler runs, missing output executable, and I/O failures.

## Language Status

**Implemented:** functions, `val`/`var`, `if`/`while`/`for`, assignments, calls, operators, implicit `stdlib.bred`, extern C function signatures, static arrays (`val arr: Int[n]`, `arr[i]`, `fun f(a: Int[])`), immutable global static arrays (`val arr: Int[n]` at top level), typeclass/instance parsing and template instantiation.

**Not implemented / restricted:** global `var`, `else if`, member access, user-defined type declarations, type inference for `val x = expr`, mutable static array declarations.

Known gaps and follow-ups: [`docs/TODO.md`](docs/TODO.md).

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
| `examples/3ac.bred` | Array-heavy demo (default CLI input) |
| `examples/simple.bred`, `max.bred`, `sandbox.bred` | Ad-hoc scratch (non-normative) |

## Tests

- **Lexer/parser/analyzer:** module tests under `lexer/`, `parser/`, and `analyzer/`.
- **Root API:** `org.nnezh.root.BredCompilationFactoryTest`.
- **C backend:** `c-backend/src/test/kotlin`; covers array pointers, local/global static arrays, string concat, C string literal escaping, and logical `!`.
- **TAC sequences** — [TACInstructionSequenceTest](tac/src/test/kotlin/org/nnezh/lltag/TACInstructionSequenceTest.kt) checks typed instructions, operands and jump targets for the [Bred fixtures](tac/src/test/resources/).
