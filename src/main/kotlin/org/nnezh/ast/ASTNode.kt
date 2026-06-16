package org.nnezh.ast

import arrow.core.Either
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.Type

sealed interface ASTNode

class EmptyNode: ASTNode

data class ProgramASTNode(
    val functions: List<DeclareFunctionASTNode>,
    val globalVariables: List<ImmutableVariableInitializationASTNode>
    ) : ASTNode

data class DeclareFunctionASTNode(
    val name: String,
    val args: FunctionArgsASTNode,
    val block: BlockASTNode,
    val resultType: Type): ASTNode

data class FunctionArgsASTNode(val arguments: List<FunctionArgumentASTNode>)
data class FunctionArgumentASTNode(val name: String, val type: Type): ASTNode
data class BlockASTNode(val statements: List<StatementASTNode>): ASTNode

sealed interface StatementASTNode: ASTNode // i.e. val x: Double = 3 + 5
data class ImmutableVariableInitializationASTNode(
    val name: String,
    val type: Type,
    val value: ExpressionASTNode): StatementASTNode

data class MutableVariableInitializationASTNode(
    val name: String,
    val type: Type,
    val value: ExpressionASTNode): StatementASTNode

data class AssignmentStatementASTNode(
    val name: String,
    val value: ExpressionASTNode
): StatementASTNode

data class IfStatementASTNode(
    val condition: ExpressionASTNode,
    val thenBlock: BlockASTNode,
    val elseBlock: Either<BlockASTNode, EmptyNode>
): StatementASTNode

data class WhileStatementASTNode(
    val condition: ExpressionASTNode,
    val bodyBlock: BlockASTNode): StatementASTNode

data class ForStatementASTNode(
    val desugaredContent: BlockASTNode
): StatementASTNode

data class CallFunctionStatementASTNode(
    val expression: ExpressionASTNode
): StatementASTNode

data class ReturnFunctionStatementASTNode(
    val expression: Either<Type.UnitType, ExpressionASTNode>
): StatementASTNode


sealed interface ExpressionASTNode: ASTNode // i.e. 3 + 5 + x

sealed interface LiteralExpressionNode: ExpressionASTNode
data class IntLiteralExpressionNode(val value: Long): LiteralExpressionNode
data class DoubleLiteralExpressionNode(val value: Double): LiteralExpressionNode
data class BooleanLiteralExpressionNode(val value: Boolean): LiteralExpressionNode
data class StringLiteralExpressionNode(val value: String): LiteralExpressionNode

data class VariableExpressionNode(val token: Token): ExpressionASTNode
data class BinaryExpressionASTNode(val left: ExpressionASTNode, val operator: Token, val right: ExpressionASTNode): ExpressionASTNode
data class UnaryExpressionASTNode(val operator: Token, val operand: ExpressionASTNode): ExpressionASTNode
data class FunctionCallExpressionNode(val name: Token, val arguments: List<ExpressionASTNode>): ExpressionASTNode