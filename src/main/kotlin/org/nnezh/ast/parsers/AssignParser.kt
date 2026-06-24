package org.nnezh.org.nnezh.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.ast.AssignmentStatementASTNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.ASTError
import org.nnezh.org.nnezh.ast.AstErrorFactory
import org.nnezh.org.nnezh.ast.TokensContext
import org.nnezh.org.nnezh.ast.match

class AssignParser(
    private val expressionParser: Parser<ExpressionASTNode>,
) : Parser<AssignmentStatementASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): AssignmentStatementASTNode {
        val lValue = parseWith(expressionParser, context)
//        val variableName: String =
//            (match<Token.Identifier>(context.consumeToken()) { token ->
//                AstErrorFactory.buildError(
//                    "variable name",
//                    token
//                )
//            }).lexeme
        match<Token.Operator.Assign>(context.consumeToken()) { token ->
            AstErrorFactory.buildError(
                "assignation",
                token
            )
        }

        val rValue: ExpressionASTNode = parseWith(expressionParser, context)

        return AssignmentStatementASTNode(lValue, rValue)
    }
}