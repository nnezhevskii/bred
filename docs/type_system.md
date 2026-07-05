# bred - Type System and Compiler Pipeline

This document describes the active type model shared by parser, semantic
analysis, template instantiation, TAC, and the C backend.

Source of truth:

- `common/src/main/kotlin/org/nnezh/bred/common/TypeSign.kt`
- `common/src/main/kotlin/org/nnezh/bred/common/BuiltInMethods.kt`
- `parser/src/main/kotlin/org/nnezh/bred/context/ProgramGlobalContext.kt`
- `parser/src/main/kotlin/org/nnezh/bred/context/ProgramTypeResolver.kt`
- `parser/src/main/kotlin/org/nnezh/bred/codegenerator/TemplateInstantiator.kt`

## 1. TypeRef vs TypeSign

`TypeRef` is parser syntax. It may be constructed outside a context because it
does not represent semantic type identity.

`TypeSign` is the semantic and backend type model. Concrete resolved types must
come from `TypeRegistry` through `ProgramGlobalContext`. Code outside
`common` should not construct concrete type implementations directly.

The common API is:

```kotlin
sealed interface TypeSign {
    val name: String
    val arguments: List<TypeSign>
    val args: List<TypeSign>
    val isPrimitive: Boolean
    val isArray: Boolean
    val isResolved: Boolean
    val elementTypeOrNull: TypeSign?
    fun render(): String
    fun toManglePart(): String
}
```

`args` is a compatibility alias for `arguments`; new code should prefer
`arguments`.

## 2. Registry Ownership

`ProgramGlobalContext` initializes and exposes:

- `intType`;
- `longType`;
- `doubleType`;
- `stringType`;
- `booleanType`;
- `unitType`;
- `arrayOf(type)`;
- `findType(name)`;
- `allTypes()`;
- `resolve(ref, genericScope)`;
- `resolveOrThrow(ref, genericScope)`;
- `builtInSignatures`.

Primitive names are `Int`, `Long`, `Double`, `String`, `Boolean`, and `Unit`.

User-defined type names are registered from `ProgramRoot.types` by
`ProgramContextCollector`. The current grammar does not produce user type
declarations, but the AST and context are prepared for them.

Generic parameters are scoped. `ProgramGlobalContext.genericScope(...)` creates
generic `TypeSign` values for a function, typeclass, or method scope. Generic
names are not global types.

## 3. Resolution Pipeline

The parser produces AST nodes whose type fields contain `TypeRef` values.
Before semantic analysis, template instantiation, TAC generation, or C
rendering, the AST must pass through `ProgramTypeResolver`.

The normal pipeline is:

```text
source
-> Lexer
-> AbstractSyntaxTreeBuilder
-> ProgramContextCollector
-> ProgramTypeResolver
-> ProgramContextCollector
-> TemplateInstantiator
-> ProgramContextCollector
-> ProgramTypeResolver
-> ProgramContextCollector
-> SemanticAnalyzer
-> LLTACGenerator
-> CTranspile
```

Template instantiation is skipped for non-template-only parser or analyzer
tests, but `ProgramTypeResolver` still runs before semantic analysis.

Unknown concrete types are rejected during resolution. Generic names resolve
only when they are present in the current generic scope.

## 4. Arrays

`Array<T>` is a semantic type created with:

```kotlin
context.arrayOf(elementType)
```

Consumers should use:

- `type.isArray`;
- `type.elementTypeOrNull`;
- `type.arguments`;
- `type.render()`;
- `type.toManglePart()`.

Code should not check `type.name == "Array"` or index into raw arguments when
the API exposes a more specific property.

Static array size is not part of `Array<T>` equality. Size belongs to the
declaration/storage layer and is carried by TAC instructions such as `ALLOC`
and `globalArray`.

## 5. Built-ins

`BuiltInMethods` stores built-ins as specs on `TypeRef` values. A
`ProgramGlobalContext` resolves those specs to `FunctionSignature` values via
`builtInSignatures`.

Semantic analysis and template instantiation use these resolved signatures from
the same context. This avoids separate built-in registries drifting apart.
Runtime string conversion built-ins include `intToString(Int): String`,
`longToString(Long): String`, and `doubleToString(Double): String`.

## 6. Template Instantiation

`TemplateInstantiator` expects a resolved AST. It infers generic mappings by
structurally matching parameter types against argument types:

- `T` maps directly to the concrete argument type;
- `Array<T>` maps from the argument element type;
- nested generic containers are traversed by their type arguments.

Inferred concrete types are normalized through `ProgramGlobalContext` before
comparison. This keeps parser-level references and context-owned types from
being treated as conflicting types when they render the same way.

Generated function names use `TypeSign.toManglePart()`. For example:

```text
findIndex(Array<Int>, Int): Int -> findIndex_Array_Int_Int_Int
```

## 7. TAC and C Backend

TAC operands and instructions use `TypeSign`; the old `base.Type` model is no
longer part of the active compiler.

`LLTACGenerator` receives the semantic expression type table and the active
`ProgramGlobalContext`. It emits:

- scalar values with their resolved primitive or named `TypeSign`;
- array variables as `Array<T>`;
- array storage sizes in `ALLOC` and `LLTACGlobalStaticArray`, not in the type.

`CTranspile` renders C types from `TypeSign` properties and maps primitives to
C storage types. Arrays use `elementTypeOrNull` for element declarations and
the size stored in TAC.

Current primitive C mappings are:

| bred type | C type |
|-----------|--------|
| `Boolean` | `bool` |
| `Int` | `int` |
| `Long` | `long long` |
| `Double` | `double` |
| `String` | `String` runtime struct |
| `Unit` | `void` |

`Long` constants are emitted as TAC `LongConst` operands and rendered to C with
an `LL` suffix.

## 8. Tests

Coverage exists at these layers:

- parser/context tests check `TypeRef`, `ProgramTypeResolver`, scoped generics,
  and built-in signature resolution;
- parser/template tests check generic inference from `Array<T>` and scalar
  `T`;
- analyzer tests check resolved global primitive types and template-generated
  code;
- TAC tests check mangled generic-array functions, `Long` lowering, and that
  internal type class names such as `TypeRef` or `PrimitiveTypeSign` do not
  leak into 3AC output;
- C backend tests check primitive rendering including `Long -> long long`;
- root tests compile `examples/3ac.bred` through the active front end.
