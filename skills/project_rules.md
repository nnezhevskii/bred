# Skill: Project Rules

This project is a compiler / language implementation project written in Kotlin.

Technology stack:

* Kotlin
* arrow-kt
* JUnit

Core rule:

* Do not change production logic unless the user explicitly asks for it.
* By default, only tests, documentation, examples, and TODO files may be changed.
* If production code appears incorrect, report the issue and propose a fix, but do not apply it without explicit approval.

Source of truth priority:

1. User's explicit instruction.
2. `docs/grammar.md`.
3. Existing tests.
4. Existing production code signatures and public API.
5. Reasonable compiler/language-design logic.

Important:

* Production code may contain bugs.
* Do not fit tests to buggy implementation behavior.
* Tests must be based on documented behavior and intended logic.
* If implementation behavior conflicts with `docs/grammar.md`, stop and report the mismatch.
* Do not silently rewrite expectations to match actual broken behavior.
* For documented features (e.g. static arrays in `docs/grammar.md`), tests and grammar take precedence over buggy production until explicitly fixed.

When uncertain:

* Do not invent language behavior.
* Add the uncertainty to `docs/TODO.md`.
* Ask the user for a decision.
