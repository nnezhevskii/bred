package org.nnezh.bred.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.AstErrorFactory
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.bred.ast.Parser
import org.nnezh.bred.ast.ReturnFunctionStatementAstNode
import org.nnezh.bred.ast.TokensContext
import org.nnezh.bred.ast.match
import org.nnezh.bred.ast.parseWith
import org.nnezh.lexer.Token

class ReturnValueParser(
    private val expressionParser: Parser<ExpressionASTNode>
): Parser<ReturnFunctionStatementAstNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): ReturnFunctionStatementAstNode {
        match<Token.Keyword.Return>(context.consumeToken()) { AstErrorFactory.buildError("return", it) }
        if (context.top() is Token.Identifier && (context.top() as Token.Identifier).lexeme == "Unit") {
            context.consumeToken()
            return ReturnFunctionStatementAstNode(null, true)
        } else if (context.top() is Token.Punctuation.RBrace) {
            return ReturnFunctionStatementAstNode(null, true)
        } else if (context.endOfInput) {
            raise(AstErrorFactory.unexpectedEOF(context.top()))
        }
        return ReturnFunctionStatementAstNode(parseWith(expressionParser, context), true)
    }
}
