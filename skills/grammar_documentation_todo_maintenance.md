# Skill: Grammar Documentation and TODO Maintenance

The project contains formal language documentation in:

`docs/grammar.md`

This file must stay synchronized with the intended language behavior.

When language syntax changes:

* update `docs/grammar.md`;
* add new grammar rules;
* remove obsolete rules;
* update examples;
* update operator precedence / associativity tables if needed;
* document restrictions and invalid forms.

Documentation rules:

* Use strict formal language.
* Prefer EBNF or EBNF-like grammar.
* Do not write marketing-style documentation.
* Do not invent syntax.
* Mark inferred behavior explicitly.
* If something is unclear, add it to `TODO.md` and ask the user.

The project also contains:

`TODO.md`

TODO maintenance rules:

* Preserve the numbering format already used in `TODO.md`.
* Continue numbering globally. Do not restart numbering.
* Remove obsolete TODO items.
* Update existing TODO items instead of duplicating them.
* Keep TODO items specific and actionable.

TODO categories:

## Urgent 🔴

Blocking bugs, broken grammar behavior, incorrect AST, incorrect lexer/parser behavior, failing essential tests.

## Nice to have 🟡

Useful improvements that are not blocking current compiler work.

## Later 🔵

Future compiler stages or features that do not belong to the current implementation phase.
Example: if the current work is AST construction, semantic analysis, type checking, name resolution, or IR generation belong here.

## Small improvements 🟢

Small cleanup, naming, formatting, minor test refactoring, documentation polish.

When adding a TODO, include:

* number;
* category;
* short title;
* context;
* expected decision or action.

When inconsistencies are found between `docs/grammar.md`, implementation, and tests:

1. Do not silently resolve them.
2. Add or update TODO.
3. Report the conflict to the user.
4. Ask whether to update documentation, tests, or implementation.
