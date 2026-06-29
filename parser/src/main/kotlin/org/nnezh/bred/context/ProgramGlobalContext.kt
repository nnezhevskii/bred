package org.nnezh.bred.context

import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.FunctionArgument
import org.nnezh.bred.common.TypeSign

data class ProgramGlobalContext(
    val types: MutableMap<String, TypeSign> = mutableMapOf(),
    val functions: MutableMap<String, FunctionMeta> = mutableMapOf(),
    val typeclasses: MutableMap<String, TypeclassMeta> = mutableMapOf(),
    val instances: MutableMap<InstanceKey, InstanceMeta> = mutableMapOf(),
) {
    fun addType(name: String, isPrimitive: Boolean) {
        types[name] = TypeSign(name) //, isPrimitive)
    }
    fun addTypeclass(name: String, genericVarName: String, methods: List<String>) {
        typeclasses[name] = TypeclassMeta(
            name = name,
            genericVarName = genericVarName,
            expectedMethods = methods
        )
    }

    fun addInstance(name: String, targetType: TypeSign, methods: List<FunctionDeclAstNode>) {
        val targetTypeName = targetType.toManglePart()
        instances[InstanceKey(name, targetTypeName)] = InstanceMeta(
            typeclassName = name,
            targetType = targetType,
            methods = methods.associateBy { it.name },
        )
    }

    fun addFunction(name: String, functionMeta: FunctionMeta) {
        functions[name] = functionMeta
    }

    fun findInstance(typeclassName: String, targetType: TypeSign): InstanceMeta? =
        instances[InstanceKey(typeclassName, targetType.toManglePart())]

}

data class TypeMeta(val name: String, val isPrimitive: Boolean)



sealed interface FunctionMeta {
    val name: String
    val isTemplate: Boolean
}

data class BuiltInFunctionMeta(
    override val name: String,
    override val isTemplate: Boolean
): FunctionMeta

data class DeclaredFunctionMeta(
    override val name: String,
    override val isTemplate: Boolean,
    val astNode: FunctionDeclAstNode
): FunctionMeta

data class TypeclassMeta(
    val name: String,
    val genericVarName: String, // "A"
    val expectedMethods: List<String> // ["toPrettyPrinter"]
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
        arguments.forEach { add(it.type.toManglePart()) }
        add(result.toManglePart())
    }.joinToString("_")

fun TypeSign.toManglePart(): String =
    if (args.isEmpty()) {
        name
    } else {
        buildList {
            add(name)
            args.forEach { add(it.toManglePart()) }
        }.joinToString("_")
    }
