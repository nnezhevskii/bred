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
* If something is unclear, add it to `docs/TODO.md` and ask the user.

The project also contains:

`docs/TODO.md`

TODO maintenance rules:

* Preserve the G-ID numbering format already used in `docs/TODO.md`.
* Continue numbering globally. Do not restart numbering.
* Remove obsolete TODO items (move to **Resolved / obsolete** section when closing).
* Update existing TODO items instead of duplicating them.
* Keep TODO items specific and actionable.

TODO sections in `docs/TODO.md`:

* **Later — semantic analysis & typechecker** — blocking semantic work
* **Later — language expansion** — non-blocking language features
* **Nice to have** — optional cleanup and docs
* **Resolved / obsolete** — closed items kept for reference

When adding a TODO, include:

* G-ID number;
* short title;
* current state;
* expected action.

When inconsistencies are found between `docs/grammar.md`, implementation, and tests:

1. Do not silently resolve them.
2. Add or update TODO.
3. Report the conflict to the user.
4. Ask whether to update documentation, tests, or implementation.
