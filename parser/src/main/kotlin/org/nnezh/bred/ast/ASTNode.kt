package org.nnezh.bred.ast

import org.nnezh.lexer.Token

sealed interface ASTNode

object EmptyNode : ASTNode

sealed interface ExpressionASTNode : ASTNode

sealed interface LiteralExpressionASTNode : ExpressionASTNode
data class IntLiteralExpressionASTNode(val value: Int) : LiteralExpressionASTNode
data class LongLiteralExpressionASTNode(val value: Long) : LiteralExpressionASTNode
data class DoubleLiteralExpressionASTNode(val value: Double) : LiteralExpressionASTNode
data class BooleanLiteralExpressionASTNode(val value: Boolean) : LiteralExpressionASTNode
data class StringLiteralExpressionASTNode(val value: String) : LiteralExpressionASTNode
data class ArrayInitializationExpressionASTNode(val args: List<ExpressionASTNode>) : ExpressionASTNode
data class VariableExpressionASTNode(val token: Token) : ExpressionASTNode, LeftValue
data class ArrayElementAccessASTNode(val name: String, val index: ExpressionASTNode) : ExpressionASTNode, LeftValue
data class FunctionCallExpressionASTNode(val name: String, val arguments: List<ExpressionASTNode>, var isConstructorCalling: Boolean = true) : ExpressionASTNode

data class BinaryExpressionASTNode(
    val left: ExpressionASTNode,
    val operator: LocatedBinaryOperator,
    val right: ExpressionASTNode
): ExpressionASTNode

data class UnaryExpressionASTNode(
    val operator: LocatedUnaryOperator,
    val operand: ExpressionASTNode,
) : ExpressionASTNode

typealias IntLiteralExpressionNode = IntLiteralExpressionASTNode
typealias LongLiteralExpressionNode = LongLiteralExpressionASTNode
typealias VariableExpressionNode = VariableExpressionASTNode
typealias StaticArrayInitializationExpressionsListNode = ArrayInitializationExpressionASTNode
