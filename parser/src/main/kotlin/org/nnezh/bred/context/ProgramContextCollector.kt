package org.nnezh.bred.context

import org.nnezh.bred.ast.ASTNode
import org.nnezh.bred.ast.DeclareProductTypeASTNode
import org.nnezh.bred.ast.DeclareSumTypeASTNode
import org.nnezh.bred.ast.DeclareTypeASTNode
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.InstanceDeclAstNode
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.ast.TokensContext
import org.nnezh.bred.ast.TypeClassDeclAstNode
import org.nnezh.bred.common.ExternalFunctionSpecs

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
            when (type) {
                is DeclareProductTypeASTNode -> {
                    val productType = context.declareProductType(type.name, type.fields)
                    context.addProductTypeConstructor(productType.name)
                }
                is DeclareSumTypeASTNode -> {
                    context.addType(type.name, isPrimitive = false)
                }
            }
        }
    }


    private fun assembleRuntimeContext(context: ProgramGlobalContext) {
        externalFunctionSpecs.forEach { spec ->
            context.addFunction(spec.name, BuiltInFunctionMeta(spec.name, false))
        }
    }

}
