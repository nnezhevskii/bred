# bred - Compiler TODO

Follow-ups for lexer, parser, analyzer, TAC, C backend, tests, and documentation.

Item IDs use the stable `G-NN` format.

## Later - semantic analysis & typechecker

| ID | Item | Current state | Action |
|----|------|---------------|--------|
| G-47 | Typeclass and instance semantic validation is incomplete | `SemanticAnalyzer` does not visit `ProgramRoot.typeClasses` or `ProgramRoot.instances`; direct visit branches are `TODO()` | Decide which checks belong in analyzer versus `TemplateInstantiator`; validate duplicate typeclasses, missing methods, method signatures, and instance target types |
| G-49 | Mixed numeric comparison behavior is unclear | `TypeValidator.produceBinaryType` allows relational operators when both operands are numeric, including `Int < Long` and `Int < Double`; docs/tests otherwise emphasize no numeric promotion for arithmetic | Decide whether mixed numeric comparison is allowed; add tests and update docs/implementation accordingly |
| G-50 | Global initializers cannot call user functions | `SemanticAnalyzer.analyze` visits global variables before registering user functions; only built-ins are registered at that point | Decide whether global initializers may call user functions; if yes, register user signatures before global variable analysis |
| G-52 | Empty array initializer has no expression type | `ArrayInitializationExpressionASTNode([])` stores no type; declarations rely on size checks and skip element-type comparison when type is null | Decide whether empty array list type should be derived from declaration context; add tests for non-zero size and wrong target contexts |
| G-68 | Typeclass calls inside instance methods are not reliably instantiated | The `examples/difficult.bred` stress program had to avoid nested calls such as `show(team.captain)` from inside `instance Printable<Team>`; `examples/type_system_flex.bred` likewise had to avoid `scoreOf(a)` from inside `instance Ranked<Verdict>`. Instance method bodies can keep unresolved typeclass-style calls after template instantiation/lowering | Define whether instance bodies may call other typeclass methods; if yes, make `TemplateInstantiator`/semantic analysis rewrite and validate those calls with the active instance context; add regression tests for nested typeclass calls in instance methods |
| G-71 | `when` is statement-oriented | Sum `when` validates bare variant names, payload patterns such as `Just(value)`, and exhaustiveness, but semantic analysis still stores `Unit` as the `when` type; TAC lowering supports statement form only | Decide whether `when` should become value-producing pattern matching; add branch result joining, then lower value-producing `when` through TAC/C |
| G-72 | Constructor namespace and nullary variant ambiguity need diagnostics | Constructors are stored by source name; a bare nullary constructor expression is accepted only when exactly one zero-argument constructor with that name is visible, and duplicate constructor names across generated concrete sums/products can become ambiguous | Define constructor namespace rules, especially for generic nullary variants; report a dedicated ambiguity diagnostic instead of falling through to `UNKNOWN_VARIABLE` |

## Later - TAC and C backend

| ID | Item | Current state | Action |
|----|------|---------------|--------|
| G-61 | Global static array initializers only lower literal elements | `LLTACGenerator` can emit global C storage for top-level static arrays with absent initializer or literal elements; non-literal element expressions are still not lowered as compile-time C initializers | Decide whether global array initializers should be literals-only at semantic level or support a compile-time expression renderer; add tests for the chosen rule |
| G-62 | Extern signature/runtime parity is incomplete | `ExternalFunctionSpecs` migrates the legacy built-in list, but `runtime.c` visibly implements only part of that surface; `examples/difficult.bred` also had to avoid `intToDouble` because the call can get through frontend stages but has no matching runtime implementation, and the legacy `doubleToString(Double, String, Int): Unit` overload cannot be represented directly as the same C symbol as `doubleToString(Double): String` | Decide whether to remove legacy buffer-style externs, add backend name mapping, or implement distinct C symbols; add a parity test that prevents extern specs and runtime exports from drifting |
| G-69 | C backend can collide with user locals in `main` | The generated C entry wrapper/signature reserves names such as `size`; a Bred `main` local declared as `val size: Int = ...` can render into C as a duplicate `int size` declaration | Mangle generated helper/parameter names or emit the user `main` body into a separately named function; add a C backend regression test for user locals named like generated wrapper variables |

## Later - language expansion

| ID | Item | Current state | Action |
|----|------|---------------|--------|
| G-03 | Type inference for variable declarations | `val x = expr` and `var x = expr` are rejected; explicit `: Type` is required | Implement inference only if the language design wants it; otherwise keep this as a permanent restriction and add negative tests |
| G-05 | `else if` chains | Parser requires `else` followed by a block | Add desugaring to nested `if` or keep unsupported with explicit negative tests |
| G-12 | Member access beyond product field chains | Lexer emits `.`, and the expression parser now consumes identifier-based product field chains such as `person.address.city`; there is still no member/method dispatch, no field access after arbitrary expressions like `makePerson().age`, and no array/member chaining such as `fighters[i].score`, which `examples/difficult.bred` had to spell via a temporary local | Decide whether current product-field-only access is the intended final scope; document restrictions or extend expression grammar and lowering |
| G-13 | Indirect calls and first-class functions | Calls attach only to identifier primaries; `(f)()` is rejected; bare function names are variables semantically | Implement first-class functions only with a full type/signature model |
| G-53 | Mutable static arrays | Only `val name: Type[n]` enters the array declaration parser branch; `var name: Type[n]` is not supported | Decide whether mutable array bindings are needed; define binding mutability versus element mutability |
| G-70 | Numeric conversion story is missing | Arithmetic is strict about exact numeric types, and there is no stable `Int -> Long` conversion helper; `examples/type_system_flex.bred` could not implement `Hashable<Int>.hashOf(value): Long` as a deterministic expression derived from `value` without mixed `Int * Long` arithmetic or an explicit conversion, so it had to use `currentTimeMillis()` instead | Decide whether the language wants explicit conversion built-ins, cast syntax, or limited widening rules; add tests for `Int` to `Long`/`Double` conversions, mixed arithmetic rejection/acceptance, and typeclass methods whose result type is wider than an input field |
| G-73 | Recursive user-defined type layouts are unspecified | Product and sum declarations can refer to user-defined types, but the current C backend lowers values as by-value structs; recursive products/sums would require an explicit pointer/box/indirection design | Decide whether recursive product/sum types are allowed; either reject cyclic layouts during resolution or introduce an owned indirection model and backend representation |

## Nice to have

| ID | Item | Current state | Action |
|----|------|---------------|--------|
| G-14 | Call statement routing | `StatementParser` routes only `identifier (` to `CallStatementParser`; the call parser itself delegates to the expression parser | Keep documented as an intentional dispatch rule or narrow the internal parser contract |
| G-16 | Package/source layout cleanup | `analyzer/src/main/java` contains Kotlin files; parser package names are current but old docs referenced obsolete packages | Clean layout when convenient |
| G-25 | Negative tests for unsupported `else if` | Parser tests currently include one `else-if` rejection case | Keep coverage when/if `if` grammar changes |
| G-56 | Top-level declaration order is split by AST list | `ProgramRoot` stores functions, globals, typeclasses, and instances in separate lists | Confirm this is acceptable for later stages; add an ordered top-level list only if order-sensitive behavior appears |
| G-57 | Documentation/test coverage for newer keywords | Lexer supports `for`, `in`, `to`, `mut`, and `type`, but older keyword coverage was written before several of these were active | Add explicit lexer assertions for all keywords and keep the grammar keyword list synchronized |

## Resolved / obsolete

| ID | Item | Resolution |
|----|------|------------|
| G-46 | Global static arrays are parsed but analyzed as scalar globals | Resolved by binding top-level `val name: T[n]` as `Array<T>` in semantic scope, validating initializer size/type, emitting `LLTACGlobalStaticArray`, and rendering global C storage before function prototypes |
| G-60 | C backend does not escape string literals | Resolved by escaping newline, carriage return, tab, quote, and backslash before emitting C string literals for `create_string(...)` / `strlen(...)`; covered by `string assignment escapes c string literal contents` |
| G-58 | Self-reference in local initializer is accepted | Resolved by tracking variables while their initializer is analyzed; self-reference now reports `UNKNOWN_VARIABLE`, including shadowing and array initializer cases |
| G-48 | Parser accepts unknown type names | Resolved by keeping parser output as `TypeRef` and adding `ProgramTypeResolver`; unknown concrete types are rejected during resolution, while generic names resolve only in generic scopes |
| G-51 | Context built-ins and semantic built-ins are split | Resolved by storing built-ins as specs and resolving `globalContext.builtInSignatures` through the same `ProgramGlobalContext` registry used by template instantiation and semantic analysis |
| G-67 | Grammar reference is stale for active product/type syntax | Resolved by updating `docs/grammar.md` to include the `type` keyword, `type mut` product declarations, constructor semantics, identifier-rooted product field access, field assignment, and current product restrictions |
| G-54 | `mut` keyword semantics | Resolved as product-type mutability: semantic analysis rejects field writes on non-`mut` product owners, while binding mutability still controls scalar rebinding |
| G-55 | Product type declarations need remaining type-system decisions | Resolved by documenting product types in grammar, type-system, and semantic docs; fields now use full `typeSign` syntax, product constructors are metadata-driven, and generic products are instantiated through `TemplateInstantiator` |
| G-63 | Product type declaration resolution is order-sensitive | Resolved by registering type names before product field resolution, refreshing concrete product metadata after forward declarations are known, and letting unknown product field types fail in `ProgramTypeResolver` instead of escaping as context collection exceptions |
| G-64 | Product mutability and field assignment rules are undefined | Resolved by defining `type mut` as field-write permission on the final owner product type and covering mutable/immutable field assignment in analyzer tests |
| G-65 | Field access type inference is incomplete in template instantiation | Resolved by inferring field-chain expression types from `ProgramGlobalContext.productFields`, including generic product field access after template instantiation |
| G-66 | Resolved generic named type arguments can be dropped | Resolved by preserving resolved arguments for named user types and adding registry coverage for `Box<Int>` rendering and mangling |

## Priority snapshot

| When | IDs |
|------|-----|
| Next pipeline | G-47 |
| Backend correctness | G-61, G-62, G-69 |
| Semantic design decisions | G-49, G-50, G-52, G-68, G-71, G-72 |
| Language growth | G-03, G-05, G-12, G-13, G-53, G-70, G-73 |
| Optional cleanup | G-14, G-16, G-25, G-56, G-57 |


