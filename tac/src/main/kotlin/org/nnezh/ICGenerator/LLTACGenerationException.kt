package org.nnezh.org.nnezh.ICGenerator

import org.nnezh.bred.ast.ASTNode
import org.nnezh.bred.ast.ArrayDeclarationASTNode
import org.nnezh.bred.ast.ArrayElementAccessASTNode
import org.nnezh.bred.ast.ArrayInitializationExpressionASTNode
import org.nnezh.bred.ast.AssignmentStatementAstNode
import org.nnezh.bred.ast.BinaryExpressionASTNode
import org.nnezh.bred.ast.BooleanLiteralExpressionASTNode
import org.nnezh.bred.ast.DoubleLiteralExpressionASTNode
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.bred.ast.FunctionCallExpressionASTNode
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.IntLiteralExpressionASTNode
import org.nnezh.bred.ast.ReturnFunctionStatementAstNode
import org.nnezh.bred.ast.ScalarVariableInitializationASTNode
import org.nnezh.bred.ast.StringLiteralExpressionASTNode
import org.nnezh.bred.ast.UnaryExpressionASTNode
import org.nnezh.bred.ast.VariableExpressionASTNode

class LLTACGenerationException(message: String) : IllegalStateException(message)

internal fun lltacError(reason: String, node: ASTNode): Nothing {
    throw LLTACGenerationException("$reason at ${node.lltacSummary()}")
}

internal fun lltacError(reason: String): Nothing {
    throw LLTACGenerationException(reason)
}

private fun ASTNode.lltacSummary(): String =
    when (this) {
        is FunctionDeclAstNode -> "function '$name'"
        is ScalarVariableInitializationASTNode -> "initializer for '$name'"
        is ArrayDeclarationASTNode -> "array declaration '$name'"
        is AssignmentStatementAstNode -> "assignment '${lValue.expressionSummary()} = ${rValue.expressionSummary()}'"
        is ReturnFunctionStatementAstNode -> "return statement"
        is ExpressionASTNode -> "expression '${expressionSummary()}'"
        else -> this::class.simpleName ?: "AST node"
    }

private fun ExpressionASTNode.expressionSummary(): String =
    when (this) {
        is BooleanLiteralExpressionASTNode -> value.toString()
        is DoubleLiteralExpressionASTNode -> value.toString()
        is IntLiteralExpressionASTNode -> value.toString()
        is StringLiteralExpressionASTNode -> "\"${value.escape()}\""
        is VariableExpressionASTNode -> token.lexeme
        is ArrayElementAccessASTNode -> "$name[${index.expressionSummary()}]"
        is ArrayInitializationExpressionASTNode -> args.joinToString(prefix = "[", postfix = "]") { it.expressionSummary() }
        is BinaryExpressionASTNode -> "${left.expressionSummary()} ${operator.lexeme} ${right.expressionSummary()}"
        is FunctionCallExpressionASTNode -> "$name(${arguments.joinToString(", ") { it.expressionSummary() }})"
        is UnaryExpressionASTNode -> "${operator.lexeme}${operand.expressionSummary()}"
    }

private fun String.escape(): String =
    replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace("\"", "\\\"")
