package org.nnezh.bred.context

import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.FunctionArgument
import org.nnezh.bred.ast.GenericParam
import org.nnezh.bred.common.ExternalFunctionSpecs
import org.nnezh.bred.common.FunctionSignature
import org.nnezh.bred.common.TypeNames
import org.nnezh.bred.common.TypeRef
import org.nnezh.bred.common.TypeRegistry
import org.nnezh.bred.common.TypeSign

class ProgramGlobalContext(
    val typeRegistry: TypeRegistry = TypeRegistry(),
    val functions: MutableMap<String, FunctionMeta> = mutableMapOf(),
    val constructors: MutableMap<String,ConstructorMeta> = mutableMapOf(),
    val typeclasses: MutableMap<String, TypeclassMeta> = mutableMapOf(),
    val instances: MutableMap<InstanceKey, InstanceMeta> = mutableMapOf(),
    val externalFunctionSpecs: List<ExternalFunctionSpecs.ExternalFunctionSpec> = ExternalFunctionSpecs.specs,
) {
    val intType: TypeSign
    val longType: TypeSign
    val doubleType: TypeSign
    val stringType: TypeSign
    val booleanType: TypeSign
    val unitType: TypeSign

    val types: Map<String, TypeSign>
        get() = typeRegistry.allTypes().associateBy { it.name }

    init {
        intType = typeRegistry.declarePrimitive(TypeNames.INT)
        longType = typeRegistry.declarePrimitive(TypeNames.LONG)
        doubleType = typeRegistry.declarePrimitive(TypeNames.DOUBLE)
        stringType = typeRegistry.declarePrimitive(TypeNames.STRING)
        booleanType = typeRegistry.declarePrimitive(TypeNames.BOOLEAN)
        unitType = typeRegistry.declarePrimitive(TypeNames.UNIT)
    }

    val externFunctionSignatures: List<FunctionSignature>
        get() = ExternalFunctionSpecs.signatures(externalFunctionSpecs) { ref -> resolveOrThrow(ref) }

    val builtInSignatures: List<FunctionSignature>
        get() = externFunctionSignatures

    fun addType(name: String, isPrimitive: Boolean): TypeSign =
        if (isPrimitive) {
            typeRegistry.declarePrimitive(name)
        } else {
            typeRegistry.declareUserType(name)
        }

    fun declareProductType(name: String, fields: Map<String, TypeSign>): TypeSign {
        val newFields: LinkedHashMap<String, TypeSign> = LinkedHashMap()
        for (field in fields) {
            newFields[field.key] = resolveOrThrow(field.value)
        }
        return typeRegistry.declareProductType(name, newFields)
    }

    fun addProductTypeConstructor(name: String) {
        val args = typeRegistry.getProductTypeFields(name).map { it.value }
        constructors[name] = ConstructorMeta(args)
    }

    fun genericType(name: String): TypeSign =
        typeRegistry.generic(name)

    fun arrayOf(elementType: TypeSign): TypeSign =
        typeRegistry.arrayOf(resolveOrThrow(elementType))

    fun findType(name: String): TypeSign? =
        typeRegistry.findType(name)

    fun allTypes(): List<TypeSign> =
        typeRegistry.allTypes()

    fun genericScope(params: List<GenericParam>): Map<String, TypeSign> =
        params.associate { it.name to genericType(it.name) }

    fun resolve(ref: TypeRef, genericScope: Map<String, TypeSign> = emptyMap()): TypeSign? =
        typeRegistry.resolve(ref, genericScope).fold(
            ifLeft = { null },
            ifRight = { it },
        )

    fun resolve(type: TypeSign, genericScope: Map<String, TypeSign> = emptyMap()): TypeSign? =
        typeRegistry.resolve(type, genericScope).fold(
            ifLeft = { null },
            ifRight = { it },
        )

    fun resolveOrThrow(ref: TypeRef, genericScope: Map<String, TypeSign> = emptyMap()): TypeSign =
        typeRegistry.resolve(ref, genericScope).fold(
            ifLeft = { error(it) },
            ifRight = { it },
        )

    fun resolveOrThrow(type: TypeSign, genericScope: Map<String, TypeSign> = emptyMap()): TypeSign =
        typeRegistry.resolve(type, genericScope).fold(
            ifLeft = { error(it) },
            ifRight = { it },
        )

    fun addTypeclass(name: String, genericVarName: String, methods: List<String>) {
        typeclasses[name] = TypeclassMeta(
            name = name,
            genericVarName = genericVarName,
            expectedMethods = methods
        )
    }

    fun addInstance(name: String, targetType: TypeSign, methods: List<FunctionDeclAstNode>) {
        val resolvedTargetType = resolveOrThrow(targetType)
        val targetTypeName = resolvedTargetType.toManglePart()
        instances[InstanceKey(name, targetTypeName)] = InstanceMeta(
            typeclassName = name,
            targetType = resolvedTargetType,
            methods = methods.associateBy { it.name },
        )
    }

    fun addFunction(name: String, functionMeta: FunctionMeta) {
        functions[name] = functionMeta
    }

    fun findInstance(typeclassName: String, targetType: TypeSign): InstanceMeta? =
        instances[InstanceKey(typeclassName, resolveOrThrow(targetType).toManglePart())]
}

data class TypeMeta(val name: String, val isPrimitive: Boolean)

data class TypeclassMeta(
    val name: String,
    val genericVarName: String,
    val expectedMethods: List<String>
)

data class InstanceKey(
    val typeclassName: String,
    val targetType: String,
)

data class InstanceMeta(
    val typeclassName: String,
    val targetType: TypeSign,
    val methods: Map<String, FunctionDeclAstNode>,
)

fun mangleFunctionName(name: String, arguments: List<FunctionArgument>, result: TypeSign): String =
    buildList {
        add(name)
        arguments.forEach { argument ->
            add(if (argument.isArray) "${TypeNames.ARRAY}_${argument.type.toManglePart()}" else argument.type.toManglePart())
        }
        add(result.toManglePart())
    }.joinToString("_")
