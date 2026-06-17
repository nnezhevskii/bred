# Skill: Review and Change Analysis

When reviewing changes, analyze what changed and whether the changes are correct according to the language grammar, project architecture, and existing conventions.

Review process:

1. Identify changed files.
2. Classify each change:

    * production logic;
    * AST model;
    * lexer;
    * parser / AST builder;
    * tests;
    * documentation;
    * examples;
    * TODO.
3. Determine the intent of the change.
4. Compare the change against:

    * `docs/grammar.md`;
    * existing test style;
    * existing AST conventions;
    * Kotlin and arrow-kt idioms;
    * compiler-design sanity.

Check for:

* incorrect AST shape;
* missing cases;
* wrong token handling;
* precedence / associativity bugs;
* incorrect error handling;
* nullable misuse;
* unsafe assumptions;
* broken arrow-kt usage;
* inconsistent naming;
* tests that only validate implementation details instead of behavior;
* documentation drift.

Restrictions:

* Do not rewrite production logic unless explicitly requested.
* If production logic seems wrong, describe the issue and add or update TODO entries.
* Do not weaken tests to make broken code pass.
* Do not delete failing tests unless they are clearly obsolete and this is documented.

Review output format:

## Summary

Briefly describe what changed.

## Findings

List issues by severity:

* Urgent 🔴
* Nice to have 🟡
* Later 🔵
* Small improvements 🟢

For each finding include:

* file;
* problem;
* expected behavior;
* actual behavior;
* recommended action.

## Test impact

Explain which tests must be added, updated, or removed.

## Documentation impact

Explain whether `docs/grammar.md` must be updated.

## TODO impact

Explain which TODO items must be added, updated, or removed.
