package org.nnezh.ast

import org.nnezh.lexer.Position
import org.nnezh.lexer.Token

fun testPos() = Position(1, 1)

fun varExpr(name: String, pos: Position = testPos()) =
    VariableExpressionNode(Token.Identifier(name, pos))

fun arrayAccess(array: String, index: ExpressionASTNode) =
    ArrayAccessExpressionASTNode(array, index)

fun initList(vararg values: ExpressionASTNode) =
    StaticArrayInitializationExpressionsListNode(values.toList())

fun assignStmt(name: String, rValue: ExpressionASTNode, pos: Position = testPos()) =
    AssignmentStatementASTNode(varExpr(name, pos), rValue)

fun AssignmentStatementASTNode.lvalueName(): String =
    (lValue as VariableExpressionNode).token.lexeme

fun AssignmentStatementASTNode.arrayAccessLValue(): ArrayAccessExpressionASTNode =
    lValue as ArrayAccessExpressionASTNode
