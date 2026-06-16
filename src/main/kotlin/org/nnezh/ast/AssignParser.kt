package org.nnezh.org.nnezh.ast

import arrow.core.raise.Raise
import org.nnezh.ast.AssignmentStatementASTNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.AstErrorFactory.buildError

class AssignParser(
    private val expressionParser: Parser<ExpressionASTNode>,
) : Parser<AssignmentStatementASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): AssignmentStatementASTNode {
        val variableName: String =
            (match<Token.Identifier>(context.consumeToken()) { token -> buildError("variable name", token) }).lexeme
        match<Token.Operator.Assign>(context.consumeToken()) { token -> buildError("assignation", token) }

        val value: ExpressionASTNode = parseWith(expressionParser, context)

        return AssignmentStatementASTNode(variableName, value)
    }
}
