package org.nnezh.bred.context

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.ArrayDeclarationASTNode
import org.nnezh.bred.ast.AssignmentStatementAstNode
import org.nnezh.bred.ast.BlockAstNode
import org.nnezh.bred.ast.ArrayElementAccessASTNode
import org.nnezh.bred.ast.ArrayInitializationExpressionASTNode
import org.nnezh.bred.ast.CallFunctionStatementAstNode
import org.nnezh.bred.ast.DeclareProductTypeASTNode
import org.nnezh.bred.ast.DeclareGlobalVariableASTNode
import org.nnezh.bred.ast.DeclareSumTypeASTNode
import org.nnezh.bred.ast.DeclareTypeASTNode
import org.nnezh.bred.ast.BinaryExpressionASTNode
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.bred.ast.FieldAccessChainExpressionASTNode
import org.nnezh.bred.ast.ForStatementAstNode
import org.nnezh.bred.ast.FunctionCallExpressionASTNode
import org.nnezh.bred.ast.FunctionArgument
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.IfStatementAstNode
import org.nnezh.bred.ast.IntLiteralExpressionASTNode
import org.nnezh.bred.ast.InstanceDeclAstNode
import org.nnezh.bred.ast.BooleanLiteralExpressionASTNode
import org.nnezh.bred.ast.DoubleLiteralExpressionASTNode
import org.nnezh.bred.ast.LongLiteralExpressionASTNode
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.ast.ReturnFunctionStatementAstNode
import org.nnezh.bred.ast.ScalarVariableInitializationASTNode
import org.nnezh.bred.ast.StatementAstNode
import org.nnezh.bred.ast.StringLiteralExpressionASTNode
import org.nnezh.bred.ast.SumTypeVariantASTNode
import org.nnezh.bred.ast.SumTypeVariantFieldASTNode
import org.nnezh.bred.ast.TypeClassDeclAstNode
import org.nnezh.bred.ast.TypeClassMethodDeclAstNode
import org.nnezh.bred.ast.UnaryExpressionASTNode
import org.nnezh.bred.ast.VariableExpressionASTNode
import org.nnezh.bred.ast.WhenBranchAstNode
import org.nnezh.bred.ast.WhenExpressionASTNode
import org.nnezh.bred.ast.WhenStatementAstNode
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

    private fun Raise<ASTError>.resolveTypeDeclaration(node: DeclareTypeASTNode): DeclareTypeASTNode =
        when (node) {
            is DeclareProductTypeASTNode -> {
                val genericScope = context.genericScope(node.genericParams)
                node.copy(fields = node.fields.mapValues { (_, type) -> resolveType(type, genericScope) })
            }
            is DeclareSumTypeASTNode -> {
                val genericScope = context.genericScope(node.genericParams)
                node.copy(variants = node.variants.map { resolveSumTypeVariant(it, genericScope) })
            }
        }

    private fun Raise<ASTError>.resolveSumTypeVariant(
        node: SumTypeVariantASTNode,
        genericScope: Map<String, TypeSign>,
    ): SumTypeVariantASTNode =
        node.copy(
            typeArguments = node.typeArguments.map { resolveType(it, genericScope) },
            fields = node.fields.map { resolveSumTypeVariantField(it, genericScope) },
        )

    private fun Raise<ASTError>.resolveSumTypeVariantField(
        node: SumTypeVariantFieldASTNode,
        genericScope: Map<String, TypeSign>,
    ): SumTypeVariantFieldASTNode =
        node.copy(type = resolveType(node.type, genericScope))

    private fun Raise<ASTError>.resolveGlobalVariable(
        node: DeclareGlobalVariableASTNode,
    ): DeclareGlobalVariableASTNode =
        node.copy(type = node.type?.let { resolveType(it, emptyMap()) })

    private fun Raise<ASTError>.resolveFunction(
        node: FunctionDeclAstNode,
        genericScope: Map<String, TypeSign>,
    ): FunctionDeclAstNode =
        node.copy(
            arguments = node.arguments.map { resolveFunctionArgument(it, genericScope) },
            result = resolveType(node.result, genericScope),
            body = resolveBlock(node.body, genericScope),
        )

    private fun Raise<ASTError>.resolveFunctionArgument(
        node: FunctionArgument,
        genericScope: Map<String, TypeSign>,
    ): FunctionArgument =
        node.copy(type = resolveType(node.type, genericScope))

    private fun Raise<ASTError>.resolveBlock(
        node: BlockAstNode,
        genericScope: Map<String, TypeSign>,
    ): BlockAstNode =
        BlockAstNode(node.statements.map { resolveStatement(it, genericScope) })

    private fun Raise<ASTError>.resolveStatement(
        node: StatementAstNode,
        genericScope: Map<String, TypeSign>,
    ): StatementAstNode =
        when (node) {
            is ScalarVariableInitializationASTNode -> node.copy(
                type = resolveType(node.type, genericScope),
                expression = resolveExpression(node.expression, genericScope),
            )
            is ArrayDeclarationASTNode -> node.copy(
                type = resolveType(node.type, genericScope),
                expression = node.expression?.let { resolveExpression(it, genericScope) },
            )
            is AssignmentStatementAstNode -> node.copy(
                lValue = resolveExpression(node.lValue, genericScope),
                rValue = resolveExpression(node.rValue, genericScope),
            )
            is CallFunctionStatementAstNode -> node.copy(expression = resolveExpression(node.expression, genericScope))
            is ReturnFunctionStatementAstNode -> node.copy(
                expression = node.expression?.let { resolveExpression(it, genericScope) },
            )
            is IfStatementAstNode -> node.copy(
                condition = resolveExpression(node.condition, genericScope),
                thenBlock = resolveBlock(node.thenBlock, genericScope),
                elseBlock = node.elseBlock?.let { resolveBlock(it, genericScope) },
            )
            is WhileStatementAstNode -> node.copy(
                condition = resolveExpression(node.condition, genericScope),
                bodyBlock = resolveBlock(node.bodyBlock, genericScope),
            )
            is ForStatementAstNode -> node.copy(desugaredContent = resolveBlock(node.desugaredContent, genericScope))
            is WhenStatementAstNode -> node.copy(
                expression = resolveExpression(node.expression, genericScope) as WhenExpressionASTNode,
            )
        }

    private fun Raise<ASTError>.resolveExpression(
        node: ExpressionASTNode,
        genericScope: Map<String, TypeSign>,
    ): ExpressionASTNode =
        when (node) {
            is IntLiteralExpressionASTNode,
            is LongLiteralExpressionASTNode,
            is DoubleLiteralExpressionASTNode,
            is BooleanLiteralExpressionASTNode,
            is StringLiteralExpressionASTNode,
            is VariableExpressionASTNode,
            is FieldAccessChainExpressionASTNode -> node
            is ArrayInitializationExpressionASTNode -> node.copy(
                args = node.args.map { resolveExpression(it, genericScope) },
            )
            is ArrayElementAccessASTNode -> node.copy(index = resolveExpression(node.index, genericScope))
            is FunctionCallExpressionASTNode -> node.copy(
                arguments = node.arguments.map { resolveExpression(it, genericScope) },
            )
            is BinaryExpressionASTNode -> node.copy(
                left = resolveExpression(node.left, genericScope),
                right = resolveExpression(node.right, genericScope),
            )
            is UnaryExpressionASTNode -> node.copy(operand = resolveExpression(node.operand, genericScope))
            is WhenExpressionASTNode -> node.copy(
                subject = resolveExpression(node.subject, genericScope),
                branches = node.branches.map { resolveWhenBranch(it, genericScope) },
            )
        }

    private fun Raise<ASTError>.resolveWhenBranch(
        node: WhenBranchAstNode,
        genericScope: Map<String, TypeSign>,
    ): WhenBranchAstNode =
        node.copy(
            condition = node.condition?.let { resolveExpression(it, genericScope) },
            bodyBlock = resolveBlock(node.bodyBlock, genericScope),
        )

    private fun Raise<ASTError>.resolveTypeClass(
        node: TypeClassDeclAstNode,
    ): TypeClassDeclAstNode {
        val genericScope = context.genericScope(listOf(node.genericParam))
        return node.copy(methods = node.methods.map { resolveTypeClassMethod(it, genericScope) })
    }

    private fun Raise<ASTError>.resolveTypeClassMethod(
        node: TypeClassMethodDeclAstNode,
        genericScope: Map<String, TypeSign>,
    ): TypeClassMethodDeclAstNode =
        node.copy(
            arguments = node.arguments.map { resolveFunctionArgument(it, genericScope) },
            result = resolveType(node.result, genericScope),
        )

    private fun Raise<ASTError>.resolveInstance(
        node: InstanceDeclAstNode,
    ): InstanceDeclAstNode =
        node.copy(
            type = resolveType(node.type, emptyMap()),
            methods = node.methods.map { resolveFunction(it, context.genericScope(it.genericParams)) },
        )

    private fun Raise<ASTError>.resolveType(
        type: TypeSign,
        genericScope: Map<String, TypeSign>,
    ): TypeSign =
        context.typeRegistry.resolve(type, genericScope).fold(
            ifLeft = { raise(ASTError(it)) },
            ifRight = { it },
        )
}
