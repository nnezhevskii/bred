package org.nnezh.bred.common

import arrow.core.Either
import arrow.core.left
import arrow.core.right

public object TypeNames {
    public const val INT: String = "Int"
    public const val DOUBLE: String = "Double"
    public const val STRING: String = "String"
    public const val BOOLEAN: String = "Boolean"
    public const val UNIT: String = "Unit"
    public const val ARRAY: String = "Array"
}

public sealed interface TypeSign {
    public val name: String
    public val arguments: List<TypeSign>
    public val args: List<TypeSign>
        get() = arguments
    public val isPrimitive: Boolean
    public val isArray: Boolean
    public val isResolved: Boolean

    public val elementTypeOrNull: TypeSign?
        get() = null

    public fun render(): String =
        if (arguments.isEmpty()) {
            name
        } else {
            arguments.joinToString(prefix = "$name<", postfix = ">") { it.render() }
        }

    public fun toManglePart(): String =
        if (arguments.isEmpty()) {
            name
        } else {
            arguments.joinToString(prefix = "${name}_") { it.toManglePart() }
        }
}

/**
 * Parser-level type reference. It is intentionally unresolved: consumers that
 * need semantic type identity must resolve it through TypeRegistry /
 * ProgramGlobalContext first.
 */
public data class TypeRef(
    override val name: String,
    override val args: List<TypeRef> = emptyList(),
) : TypeSign {
    override val arguments: List<TypeSign>
        get() = args
    override val isPrimitive: Boolean
        get() = false
    override val isArray: Boolean
        get() = false
    override val isResolved: Boolean
        get() = false
}

internal data class PrimitiveTypeSign internal constructor(
    override val name: String,
) : TypeSign {
    override val arguments: List<TypeSign>
        get() = emptyList()
    override val isPrimitive: Boolean
        get() = true
    override val isArray: Boolean
        get() = false
    override val isResolved: Boolean
        get() = true
}

internal data class NamedTypeSign internal constructor(
    override val name: String,
    override val arguments: List<TypeSign> = emptyList(),
) : TypeSign {
    override val isPrimitive: Boolean
        get() = false
    override val isArray: Boolean
        get() = false
    override val isResolved: Boolean
        get() = true
}

internal data class GenericTypeSign internal constructor(
    override val name: String,
) : TypeSign {
    override val arguments: List<TypeSign>
        get() = emptyList()
    override val isPrimitive: Boolean
        get() = false
    override val isArray: Boolean
        get() = false
    override val isResolved: Boolean
        get() = true
}

internal data class ArrayTypeSign internal constructor(
    override val elementTypeOrNull: TypeSign,
) : TypeSign {
    override val name: String
        get() = TypeNames.ARRAY
    override val arguments: List<TypeSign>
        get() = listOf(elementTypeOrNull)
    override val isPrimitive: Boolean
        get() = false
    override val isArray: Boolean
        get() = true
    override val isResolved: Boolean
        get() = true
}

public fun TypeSign.toTypeRef(): TypeRef =
    TypeRef(name, arguments.map { it.toTypeRef() })

public class TypeRegistry {
    private val namedTypes: LinkedHashMap<String, TypeSign> = linkedMapOf()
    private val arrayTypes: MutableMap<TypeSign, TypeSign> = mutableMapOf()

    public fun declarePrimitive(name: String): TypeSign =
        namedTypes.getOrPut(name) { PrimitiveTypeSign(name) }.also { existing ->
            require(existing.isPrimitive) { "Type '$name' is already declared as non-primitive" }
        }

    public fun declareUserType(name: String): TypeSign =
        namedTypes.getOrPut(name) { NamedTypeSign(name) }.also { existing ->
            require(!existing.isPrimitive) { "Primitive type '$name' cannot be redeclared as user type" }
        }

    public fun generic(name: String): TypeSign =
        GenericTypeSign(name)

    public fun arrayOf(elementType: TypeSign): TypeSign {
        require(elementType.isResolved) { "Array element type must be resolved, got ${elementType.render()}" }
        return arrayTypes.getOrPut(elementType) { ArrayTypeSign(elementType) }
    }

    public fun findType(name: String): TypeSign? =
        namedTypes[name]

    public fun allTypes(): List<TypeSign> =
        namedTypes.values.toList() + arrayTypes.values.toList()

    public fun resolve(
        ref: TypeSign,
        genericScope: Map<String, TypeSign> = emptyMap(),
    ): Either<String, TypeSign> {
        if (ref.isResolved) {
            return resolveResolved(ref, genericScope)
        }
        return resolve(ref.toTypeRef(), genericScope)
    }

    public fun resolve(
        ref: TypeRef,
        genericScope: Map<String, TypeSign> = emptyMap(),
    ): Either<String, TypeSign> {
        genericScope[ref.name]?.let { generic ->
            return if (ref.args.isEmpty()) {
                generic.right()
            } else {
                "Generic type '${ref.name}' cannot have type arguments".left()
            }
        }

        if (ref.name == TypeNames.ARRAY) {
            if (ref.args.size != 1) {
                return "Array type expects exactly one type argument".left()
            }
            val elementType = resolve(ref.args.single(), genericScope)
                .fold(ifLeft = { return it.left() }, ifRight = { it })
            return arrayOf(elementType).right()
        }

        val baseType = namedTypes[ref.name]
            ?: return "Unknown type '${ref.name}'".left()
        if (ref.args.isEmpty()) {
            return baseType.right()
        }

        val resolvedArgs = ref.args.map { arg ->
            resolve(arg, genericScope).fold(ifLeft = { return it.left() }, ifRight = { it })
        }
        return NamedTypeSign(baseType.name, resolvedArgs).right()
    }

    private fun resolveResolved(
        type: TypeSign,
        genericScope: Map<String, TypeSign>,
    ): Either<String, TypeSign> {
        if (type.isArray) {
            val elementType = type.elementTypeOrNull
                ?: return "Array type '${type.render()}' has no element type".left()
            val resolvedElement = resolve(elementType, genericScope)
                .fold(ifLeft = { return it.left() }, ifRight = { it })
            return arrayOf(resolvedElement).right()
        }

        if (type.arguments.isEmpty()) {
            return type.right()
        }

        val resolvedArgs = type.arguments.map { argument ->
            resolve(argument, genericScope).fold(ifLeft = { return it.left() }, ifRight = { it })
        }
        return NamedTypeSign(type.name, resolvedArgs).right()
    }
}
