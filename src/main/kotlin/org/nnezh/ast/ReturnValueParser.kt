package org.nnezh.org.nnezh.ast

import arrow.core.left
import arrow.core.raise.Raise
import arrow.core.right
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.ReturnFunctionStatementASTNode
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.Type

class ReturnValueParser(
    private val expressionParser: Parser<ExpressionASTNode>
): Parser<ReturnFunctionStatementASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): ReturnFunctionStatementASTNode {
        match<Token.Keyword.Return>(context.consumeToken()) { AstErrorFactory.buildError("return", it) }
        if (context.top() is Token.Identifier && (context.top() as Token.Identifier).lexeme == Type.toString(Type.UnitType)) {
            context.consumeToken()
            return ReturnFunctionStatementASTNode(Type.UnitType.left())
        } else if (context.top() is Token.Punctuation.RBrace) {
            return ReturnFunctionStatementASTNode(Type.UnitType.left())
        } else if (context.endOfInput) {
            raise(AstErrorFactory.unexpectedEOF(context.top()))
        }
        return ReturnFunctionStatementASTNode(parseWith(expressionParser, context).right())
    }
}