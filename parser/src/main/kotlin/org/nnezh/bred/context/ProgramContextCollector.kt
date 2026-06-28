package org.nnezh.bred.context

import org.nnezh.bred.ast.ASTNode
import org.nnezh.bred.ast.DeclareTypeASTNode
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.InstanceDeclAstNode
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.ast.TokensContext
import org.nnezh.bred.ast.TypeClassDeclAstNode

class ProgramContextCollector {
    private var context: ProgramGlobalContext = ProgramGlobalContext()

    fun collect(root: ProgramRoot): ProgramGlobalContext {
        context = ProgramGlobalContext()
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
        // TODO: didn't handle args for target type (Array<Int> - show error)
        instances.forEach {
            context.addInstance(it.typeClassName, it.type, it.methods)
        }
    }

    private fun handleTypes(types: List<DeclareTypeASTNode>) {
        if ((types.isEmpty())) {
            return
        }
        TODO()
    }


    private fun assembleRuntimeContext(context: ProgramGlobalContext) {
        listOf("Int", "Double", "String", "Boolean").forEach { context.addType(it, true) }

        context.addFunction("println", BuiltInFunctionMeta("println", false))
    }

}
