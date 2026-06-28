package org.nnezh.bred.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.ArrayElementAccessASTNode
import org.nnezh.bred.ast.AssignmentStatementAstNode
import org.nnezh.bred.ast.AstErrorFactory
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.bred.ast.Parser
import org.nnezh.bred.ast.TokensContext
import org.nnezh.bred.ast.VariableExpressionASTNode
import org.nnezh.bred.ast.match
import org.nnezh.bred.ast.parseWith
import org.nnezh.lexer.Token

class AssignParser(
    private val expressionParser: Parser<ExpressionASTNode>,
) : Parser<AssignmentStatementAstNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): AssignmentStatementAstNode {
        val lValue = parseWith(expressionParser, context)
        if (lValue !is VariableExpressionASTNode && lValue !is ArrayElementAccessASTNode) {
            raise(AstErrorFactory.buildError("assignable expression", context.top()))
        }
        match<Token.Operator.Assign>(context.consumeToken()) { token ->
            AstErrorFactory.buildError("assignation", token)
        }

        val rValue: ExpressionASTNode = parseWith(expressionParser, context)
        return AssignmentStatementAstNode(lValue, rValue)
    }
}
