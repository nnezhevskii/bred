# bred - Type System and Compiler Pipeline

This document describes the active type model shared by parser, semantic
analysis, template instantiation, TAC, and the C backend.

Source of truth:

- `common/src/main/kotlin/org/nnezh/bred/common/TypeSign.kt`
- `common/src/main/kotlin/org/nnezh/bred/common/ExternalFunctionSpecs.kt`
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
- `productFields(type)`;
- `isMutableProduct(type)`;
- `sumDefinition(type)`;
- `constructorsByName(name)`;
- `externFunctionSignatures`.
- `builtInSignatures` as a compatibility alias.

Primitive names are `Int`, `Long`, `Double`, `String`, `Boolean`, and `Unit`.

User-defined product and sum type names are registered from `ProgramRoot.types`
by `ProgramContextCollector`. Product fields, sum variants, constructors, and
mutability metadata live in `ProgramGlobalContext` so semantic analysis,
template instantiation, TAC, and the C backend use one shared view of the
program's declared types.

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
-> parse root-level stdlib.bred
-> merge stdlib ProgramRoot before user ProgramRoot
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

## 5. Product Types

Product declarations create named semantic types plus product metadata:

```bred
type Person(name: String, age: Int)
type Box<T>(value: T)
type mut Counter(value: Int)
```

`ProgramContextCollector` registers all declared type names before resolving
product fields. This allows product fields to refer to product types declared
later in the file. Resolved product metadata is stored as
`ProductTypeDefinition`, and product constructor metadata is stored as
`ConstructorMeta(kind = PRODUCT)`.

Generic product declarations are treated as templates. A concrete use such as
`Box<Int>(1)` is instantiated by `TemplateInstantiator`, which creates a
concrete generated declaration and registers it in the active context. Generated
product names use the usual type mangling, for example `Box_Int` for
`Box<Int>`.

Product fields use full `typeSign` syntax. `TypeRegistry.resolveResolved`
preserves type arguments for resolved named user types, so `Box<Int>` and
`Box<String>` remain distinct semantic types.

`mut` is product-type mutability. Semantic analysis allows assignment through a
field chain only when the final owner product type was declared with `mut`:

```bred
type mut Counter(value: Int)

fun inc(counter: Counter): Unit {
    counter.value = counter.value + 1
}
```

The root variable binding may be `val`; binding mutability prevents rebinding a
variable name, not writes to a mutable product's fields.

TAC emits product layouts as `STRUCT_DEF` / `FIELD` metadata. The C backend
renders products as by-value C structs and product constructor calls as compound
literals.

## 6. Sum Types

Sum declarations create one named owner type and a set of variants:

```bred
type MaybeInt = Nothing | Just<Int>
type Maybe<T> = Just<T>(value: T) | Nothing
```

Each variant becomes constructor metadata with
`ConstructorMeta(kind = SUM_VARIANT)`. Named variant fields become constructor
arguments in declaration order. A variant with only type arguments uses
synthetic payload field names `value0`, `value1`, and so on, so `Just<Int>` is a
single-argument constructor with payload field `value0`.

Generic sum types are templates. When a generic variant constructor is used with
explicit type arguments, `TemplateInstantiator` instantiates the owner sum type
and registers concrete sum metadata in the active context. The variant function
name remains the source variant name, while the result type is the concrete sum
owner type.

The active `when` support is statement-oriented:

- for a sum-typed subject, branch conditions written as bare variant names are
  checked against the subject's variants;
- variant patterns such as `Just(payload)` and `Success(code, message)` bind
  payload fields positionally as immutable branch-local variables; binding
  names must be unique and may not redeclare visible variables;
- a sum `when` without `else` must cover all variants exactly once;
- the semantic type of `when` is `Unit`.

TAC lowers sum declarations to by-value tagged structs. The generated struct
has a `tag: Int` field plus flattened payload fields named
`<Variant>_<field>`, for example `Just_value0`. TAC emits `SUM_VARIANT` and
`SUM_VARIANT_FIELD` metadata so the C backend can render variant constructor
calls as designated compound literals such as:

```c
(MaybeInt){.tag = 1, .Just_value0 = value}
```

For sum `when` payload patterns, TAC first checks the `tag` and then emits
`LOAD_FIELD` instructions from flattened payload fields into the branch-local
binding variables.

The current backend does not use a heap allocation, pointer, or C union model
for sum values.

## 7. Standard Library and Extern Functions

Every root compile implicitly parses root-level `stdlib.bred` and prepends its
AST declarations to the user program before type resolution. Bred stdlib
functions, typeclasses, instances, and globals participate in the same
resolution, template instantiation, semantic analysis, TAC generation, and C
rendering as user declarations.

`ExternalFunctionSpecs` stores C runtime function signatures as specs on
`TypeRef` values. A `ProgramGlobalContext` resolves those specs to
`FunctionSignature` values via `externFunctionSignatures`; `builtInSignatures`
is retained as a compatibility alias.

Semantic analysis and template instantiation use these resolved signatures from
the same context. This avoids separate extern registries drifting apart.
Runtime string conversion externs include `intToString(Int): String`,
`longToString(Long): String`, and `doubleToString(Double): String`.

## 8. Template Instantiation

`TemplateInstantiator` expects a resolved AST. It infers generic mappings by
structurally matching parameter types against argument types:

- `T` maps directly to the concrete argument type;
- `Array<T>` maps from the argument element type;
- product and sum template owners map from their generated concrete type back to
  the template declaration;
- nested generic containers are traversed by their type arguments;
- product field chains can contribute inference through their resolved field
  type.

Inferred concrete types are normalized through `ProgramGlobalContext` before
comparison. This keeps parser-level references and context-owned types from
being treated as conflicting types when they render the same way.

Generated function names use `TypeSign.toManglePart()`. For example:

```text
findIndex(Array<Int>, Int): Int -> findIndex_Array_Int_Int_Int
```

## 9. TAC and C Backend

TAC operands and instructions use `TypeSign`; the old `base.Type` model is no
longer part of the active compiler.

`LLTACGenerator` receives the semantic expression type table and the active
`ProgramGlobalContext`. It emits:

- scalar values with their resolved primitive or named `TypeSign`;
- array variables as `Array<T>`;
- array storage sizes in `ALLOC` and `LLTACGlobalStaticArray`, not in the type;
- product and sum layouts as struct metadata;
- sum variant metadata used by the C backend to render constructors.

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

## 10. Tests

Coverage exists at these layers:

- parser/context tests check `TypeRef`, `ProgramTypeResolver`, scoped generics,
  user-defined product/sum types, and built-in signature resolution;
- parser/template tests check generic inference from `Array<T>`, scalar `T`,
  product fields, and generic product/sum constructors;
- analyzer tests check resolved global primitive types and template-generated
  code, product field mutability, sum constructors, and sum `when`
  exhaustiveness/payload binding;
- TAC tests check mangled generic-array functions, `Long` lowering, and that
  internal type class names such as `TypeRef` or `PrimitiveTypeSign` do not
  leak into 3AC output; they also cover product/sum struct metadata and sum
  `when` tag/payload lowering;
- C backend tests check primitive rendering including `Long -> long long` and
  sum constructor compound literals;
- root tests compile `examples/3ac.bred` through the active front end.
