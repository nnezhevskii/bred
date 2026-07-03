package org.nnezh.bred.context

import arrow.core.Either
import arrow.core.raise.either
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.ArrayDeclarationASTNode
import org.nnezh.bred.ast.AssignmentStatementAstNode
import org.nnezh.bred.ast.BlockAstNode
import org.nnezh.bred.ast.CallFunctionStatementAstNode
import org.nnezh.bred.ast.DeclareGlobalVariableASTNode
import org.nnezh.bred.ast.DeclareTypeASTNode
import org.nnezh.bred.ast.ForStatementAstNode
import org.nnezh.bred.ast.FunctionArgument
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.IfStatementAstNode
import org.nnezh.bred.ast.InstanceDeclAstNode
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.ast.ReturnFunctionStatementAstNode
import org.nnezh.bred.ast.ScalarVariableInitializationASTNode
import org.nnezh.bred.ast.StatementAstNode
import org.nnezh.bred.ast.TypeClassDeclAstNode
import org.nnezh.bred.ast.TypeClassMethodDeclAstNode
import org.nnezh.bred.ast.WhileStatementAstNode
import org.nnezh.bred.common.TypeSign

class ProgramTypeResolver(
    private val context: ProgramGlobalContext,
) {
    fun resolve(root: ProgramRoot): Either<ASTError, ProgramRoot> = either {
        ProgramRoot(
            functions = root.functions.map { resolveFunction(it, context.genericScope(it.genericParams)) }.toMutableList(),
            globalVariables = root.globalVariables.map { resolveGlobalVariable(it) },
            types = root.types.map { resolveTypeDeclaration(it) },
            typeClasses = root.typeClasses.map { resolveTypeClass(it) },
            instances = root.instances.map { resolveInstance(it) },
        )
    }

    private fun resolveTypeDeclaration(node: DeclareTypeASTNode): DeclareTypeASTNode =
        node

    private fun arrow.core.raise.Raise<ASTError>.resolveGlobalVariable(
        node: DeclareGlobalVariableASTNode,
    ): DeclareGlobalVariableASTNode =
        node.copy(type = node.type?.let { resolveType(it, emptyMap()) })

    private fun arrow.core.raise.Raise<ASTError>.resolveFunction(
        node: FunctionDeclAstNode,
        genericScope: Map<String, TypeSign>,
    ): FunctionDeclAstNode =
        node.copy(
            arguments = node.arguments.map { resolveFunctionArgument(it, genericScope) },
            result = resolveType(node.result, genericScope),
            body = resolveBlock(node.body, genericScope),
        )

    private fun arrow.core.raise.Raise<ASTError>.resolveFunctionArgument(
        node: FunctionArgument,
        genericScope: Map<String, TypeSign>,
    ): FunctionArgument =
        node.copy(type = resolveType(node.type, genericScope))

    private fun arrow.core.raise.Raise<ASTError>.resolveBlock(
        node: BlockAstNode,
        genericScope: Map<String, TypeSign>,
    ): BlockAstNode =
        BlockAstNode(node.statements.map { resolveStatement(it, genericScope) })

    private fun arrow.core.raise.Raise<ASTError>.resolveStatement(
        node: StatementAstNode,
        genericScope: Map<String, TypeSign>,
    ): StatementAstNode =
        when (node) {
            is ScalarVariableInitializationASTNode -> node.copy(type = resolveType(node.type, genericScope))
            is ArrayDeclarationASTNode -> node.copy(type = resolveType(node.type, genericScope))
            is AssignmentStatementAstNode -> node
            is CallFunctionStatementAstNode -> node
            is ReturnFunctionStatementAstNode -> node
            is IfStatementAstNode -> node.copy(
                thenBlock = resolveBlock(node.thenBlock, genericScope),
                elseBlock = node.elseBlock?.let { resolveBlock(it, genericScope) },
            )
            is WhileStatementAstNode -> node.copy(bodyBlock = resolveBlock(node.bodyBlock, genericScope))
            is ForStatementAstNode -> node.copy(desugaredContent = resolveBlock(node.desugaredContent, genericScope))
        }

    private fun arrow.core.raise.Raise<ASTError>.resolveTypeClass(
        node: TypeClassDeclAstNode,
    ): TypeClassDeclAstNode {
        val genericScope = context.genericScope(listOf(node.genericParam))
        return node.copy(methods = node.methods.map { resolveTypeClassMethod(it, genericScope) })
    }

    private fun arrow.core.raise.Raise<ASTError>.resolveTypeClassMethod(
        node: TypeClassMethodDeclAstNode,
        genericScope: Map<String, TypeSign>,
    ): TypeClassMethodDeclAstNode =
        node.copy(
            arguments = node.arguments.map { resolveFunctionArgument(it, genericScope) },
            result = resolveType(node.result, genericScope),
        )

    private fun arrow.core.raise.Raise<ASTError>.resolveInstance(
        node: InstanceDeclAstNode,
    ): InstanceDeclAstNode =
        node.copy(
            type = resolveType(node.type, emptyMap()),
            methods = node.methods.map { resolveFunction(it, context.genericScope(it.genericParams)) },
        )

    private fun arrow.core.raise.Raise<ASTError>.resolveType(
        type: TypeSign,
        genericScope: Map<String, TypeSign>,
    ): TypeSign =
        context.typeRegistry.resolve(type, genericScope).fold(
            ifLeft = { raise(ASTError(it)) },
            ifRight = { it },
        )
}
