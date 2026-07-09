package org.nnezh.bred.context

import org.nnezh.bred.ast.ASTNode
import org.nnezh.bred.ast.DeclareProductTypeASTNode
import org.nnezh.bred.ast.DeclareSumTypeASTNode
import org.nnezh.bred.ast.DeclareTypeASTNode
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.InstanceDeclAstNode
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.ast.TypeClassDeclAstNode
import org.nnezh.bred.common.ExternalFunctionSpecs
import org.nnezh.bred.common.TypeSign

class ProgramContextCollector(
    private val externalFunctionSpecs: List<ExternalFunctionSpecs.ExternalFunctionSpec> = ExternalFunctionSpecs.specs,
) {
    private var context: ProgramGlobalContext = ProgramGlobalContext(externalFunctionSpecs = externalFunctionSpecs)

    fun collect(root: ProgramRoot): ProgramGlobalContext {
        context = ProgramGlobalContext(externalFunctionSpecs = externalFunctionSpecs)
        assembleRuntimeContext(context)

        visit(root)
        return context
    }

    private fun visit(root: ProgramRoot) {
        handleTypes(root.types)
        handleFunctions(root.functions)
        handleTypeClasses(root.typeClasses)
        handleInstances(root.instances)
    }

    private fun handleTypeClasses(typeClasses: List<TypeClassDeclAstNode>) {
        typeClasses.forEach { typeClass ->
            context.addTypeclass(typeClass.name, typeClass.genericParam.name, typeClass.methods.map { it.name } )
        }
    }

    private fun handleFunctions(functions: List<FunctionDeclAstNode>) {
        functions.forEach { functionDecl ->
            context.addFunction(
                functionDecl.name,
                DeclaredFunctionMeta(
                    functionDecl.name,
                    functionDecl.genericParams.isNotEmpty(),
                    functionDecl)
            )
        }
    }

    private fun handleInstances(instances: List<InstanceDeclAstNode>) {
        instances.forEach {
            context.addInstance(it.typeClassName, it.type, it.methods)
        }
    }

    private fun handleTypes(types: List<DeclareTypeASTNode>) {
        types.forEach { type ->
            context.addType(type.name, isPrimitive = false)
        }

        val concreteProducts = types
            .filterIsInstance<DeclareProductTypeASTNode>()
            .filter { it.genericParams.isEmpty() }

        repeat(2) {
            concreteProducts.filter { canDeclareProductType(it) }.forEach { type ->
                context.declareProductType(type.name, type.fields, type.isMutable)
            }
        }
        concreteProducts.filter { canDeclareProductType(it) }.forEach { type ->
            context.addProductTypeConstructor(type.name)
        }

        types.filterIsInstance<DeclareSumTypeASTNode>().forEach { type ->
            if (canDeclareSumType(type)) {
                context.declareSumType(type.name, type.genericParams, type.variants)
            }
        }
    }

    private fun canDeclareProductType(type: DeclareProductTypeASTNode): Boolean =
        type.fields.values.all { context.resolve(it) != null }

    private fun canDeclareSumType(type: DeclareSumTypeASTNode): Boolean {
        val payloadTypes = type.variants.flatMap { variant ->
            if (variant.fields.isNotEmpty()) {
                variant.fields.map { it.type }
            } else {
                variant.typeArguments
            }
        }
        val genericScope = context.genericScope(type.genericParams)
        val payloadsCanResolve = payloadTypes.all { context.resolve(it, genericScope) != null }
        val declarationReady = type.genericParams.isEmpty() || payloadTypes.all(TypeSign::isResolved)
        return declarationReady && payloadsCanResolve
    }


    private fun assembleRuntimeContext(context: ProgramGlobalContext) {
        externalFunctionSpecs.forEach { spec ->
            context.addFunction(spec.name, BuiltInFunctionMeta(spec.name, false))
        }
    }

}
