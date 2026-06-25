# Skill: Test Case Generation

When writing or updating tests, focus on intended language behavior, not current buggy implementation behavior.

Main rule:

* Tests must follow `docs/grammar.md` and reasonable compiler behavior.
* Use production code only to understand function signatures, constructors, public APIs, and existing test style.
* Do not fit expected results to broken actual results.

For every new language feature, add comprehensive tests.

Test categories:

## Positive tests

Cover valid syntax and expected AST shape.

Check:

* root node type;
* exact node types;
* order of nodes;
* nesting;
* identifiers;
* literals;
* operators;
* precedence;
* associativity;
* blocks;
* declarations;
* statements;
* expressions;
* error-free parsing.

## Negative tests

Cover invalid syntax and expected diagnostics.

Check:

* lexer errors;
* parser errors;
* AST builder errors;
* missing tokens;
* malformed expressions;
* invalid nesting;
* unsupported constructs;
* unterminated strings / blocks / parentheses;
* invalid operator placement.

## Regression tests

Add tests for bugs or inconsistencies found during review.

## Integration tests

When needed, test the full pipeline:
source code string or `.bred` file
→ lexer
→ parser / AST builder
→ AST assertions.

Rules:

* Do not write tests that only check `ast != null`.
* Do not use broad snapshot tests as the only verification.
* Prefer explicit structural assertions.
* Keep test names descriptive.
* Follow the existing JUnit style in the project.
* Keep tests readable even if they are detailed.
* If current implementation fails a correct test, do not change the expectation. Report the mismatch.

**Array tests:** see `TypeCheckerTest` region **Arrays**, `VariableScopeAnalyzerTest` region **Arrays**, and parser tests in `ImmutableInitializationParserTest`, `AssignParserTest`, `FunctionParserTest`, `AbstractSyntaxTreeExpressionParserTest` as reference style for new language features.

Before writing tests:

1. Read `docs/grammar.md`.
2. Inspect existing tests for style.
3. Inspect production APIs only for signatures and usage.
4. Decide expected behavior from documentation and language logic.
5. Write tests accordingly.

If documentation is missing or ambiguous:

* Add a TODO entry.
* Ask the user for clarification.
* Do not invent behavior silently.
