# bred - Grammar / Parser / Analyzer TODO

Follow-ups for lexer, parser, analyzer, tests, and documentation.

Item IDs use the stable `G-NN` format.

## Later - semantic analysis & typechecker

| ID | Item | Current state | Action |
|----|------|---------------|--------|
| G-46 | Global static arrays are parsed but analyzed as scalar globals | `ProgramParser` converts top-level array `val` to `DeclareGlobalVariableASTNode(size = n)`, while `SemanticAnalyzer` global-variable handling puts `node.type!!` directly into scope and ignores `size` | Handle global arrays as `Array<elementType>`; add tests for global array read, pass, size mismatch, and incompatible initializer |
| G-47 | Typeclass and instance semantic validation is incomplete | `SemanticAnalyzer` does not visit `ProgramRoot.typeClasses` or `ProgramRoot.instances`; direct visit branches are `TODO()` | Decide which checks belong in analyzer versus `TemplateInstantiator`; validate duplicate typeclasses, missing methods, method signatures, and instance target types |
| G-48 | Parser accepts unknown type names | `TypeSignParser` accepts any identifier/generic type sign; analyzer primarily relies on context and exact `TypeSign` equality | Decide whether unknown concrete types should be rejected before/inside semantic analysis; keep generic parameters valid in generic scopes |
| G-49 | Mixed numeric comparison behavior is unclear | `TypeValidator.produceBinaryType` allows relational operators when both operands are numeric, including `Int < Double`; docs/tests otherwise emphasize no numeric promotion for arithmetic | Decide whether mixed numeric comparison is allowed; add tests and update docs/implementation accordingly |
| G-50 | Global initializers cannot call user functions | `SemanticAnalyzer.analyze` visits global variables before registering user functions; only built-ins are registered at that point | Decide whether global initializers may call user functions; if yes, register user signatures before global variable analysis |
| G-51 | Context built-ins and semantic built-ins are split | `ProgramContextCollector` adds only `println`; `SemanticAnalyzer` registers the larger `common.BuiltInMethods.functions` list separately | Consolidate or document the ownership boundary so template instantiation and semantic analysis cannot diverge |
| G-52 | Empty array initializer has no expression type | `ArrayInitializationExpressionASTNode([])` stores no type; declarations rely on size checks and skip element-type comparison when type is null | Decide whether empty array list type should be derived from declaration context; add tests for non-zero size and wrong target contexts |

## Later - language expansion

| ID | Item | Current state | Action |
|----|------|---------------|--------|
| G-03 | Type inference for variable declarations | `val x = expr` and `var x = expr` are rejected; explicit `: Type` is required | Implement inference only if the language design wants it; otherwise keep this as a permanent restriction and add negative tests |
| G-05 | `else if` chains | Parser requires `else` followed by a block | Add desugaring to nested `if` or keep unsupported with explicit negative tests |
| G-12 | Member access | Lexer emits `.`, but expression grammar does not consume it | Implement member access or remove/document the dot token as reserved |
| G-13 | Indirect calls and first-class functions | Calls attach only to identifier primaries; `(f)()` is rejected; bare function names are variables semantically | Implement first-class functions only with a full type/signature model |
| G-53 | Mutable static arrays | Only `val name: Type[n]` enters the array declaration parser branch; `var name: Type[n]` is not supported | Decide whether mutable array bindings are needed; define binding mutability versus element mutability |
| G-54 | `mut` keyword | Lexer reserves `mut`, parser never consumes it | Decide whether `mut` should be removed, reserved for future syntax, or implemented |
| G-55 | User-defined type declarations | `ProgramRoot.types` and `DeclareTypeASTNode` exist, but current parser never creates type declarations | Define syntax or remove/defer the AST field |

## Nice to have

| ID | Item | Current state | Action |
|----|------|---------------|--------|
| G-14 | Call statement routing | `StatementParser` routes only `identifier (` to `CallStatementParser`; the call parser itself delegates to the expression parser | Keep documented as an intentional dispatch rule or narrow the internal parser contract |
| G-16 | Package/source layout cleanup | `analyzer/src/main/java` contains Kotlin files; parser package names are current but old docs referenced obsolete packages | Clean layout when convenient |
| G-25 | Negative tests for unsupported `else if` | Parser tests currently include one `else-if` rejection case | Keep coverage when/if `if` grammar changes |
| G-56 | Top-level declaration order is split by AST list | `ProgramRoot` stores functions, globals, typeclasses, and instances in separate lists | Confirm this is acceptable for later stages; add an ordered top-level list only if order-sensitive behavior appears |
| G-57 | Documentation/test coverage for `to` and `in` keywords | Lexer supports `for`, `in`, `to`, but the keyword test string currently omits `for`, `in`, `to`, and `mut` | Add explicit lexer assertions for all keywords |

## Priority snapshot

| When | IDs |
|------|-----|
| Next pipeline | G-46, G-47 |
| Semantic design decisions | G-48, G-49, G-50, G-51, G-52 |
| Language growth | G-03, G-05, G-12, G-13, G-53, G-54, G-55 |
| Optional cleanup | G-14, G-16, G-25, G-56, G-57 |
