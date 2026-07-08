package org.nnezh.bred.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.AstErrorFactory.buildError
import org.nnezh.bred.ast.BlockAstNode
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.bred.ast.Parser
import org.nnezh.bred.ast.TokensContext
import org.nnezh.bred.ast.WhenBranchAstNode
import org.nnezh.bred.ast.WhenExpressionASTNode
import org.nnezh.bred.ast.match
import org.nnezh.bred.ast.parseWith
import org.nnezh.lexer.Token

class WhenParser(
    private val expressionParser: Lazy<Parser<ExpressionASTNode>>,
    private val blockParser: Lazy<Parser<BlockAstNode>>,
) : Parser<WhenExpressionASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): WhenExpressionASTNode {
        match<Token.Keyword.When>(context.consumeToken()) { buildError("when", it) }
        match<Token.Punctuation.LParen>(context.consumeToken()) { buildError("(", it) }
        val subject = parseWith(expressionParser.value, context)
        match<Token.Punctuation.RParen>(context.consumeToken()) { buildError(")", it) }
        match<Token.Punctuation.LBrace>(context.consumeToken()) { buildError("{", it) }

        val branches = mutableListOf<WhenBranchAstNode>()
        var seenElse = false
        while (context.top() !is Token.Punctuation.RBrace) {
            if (context.endOfInput) {
                raise(ASTError("Unexpected end of file in when expression at ${context.top().position}"))
            }

            val condition = if (context.top() is Token.Keyword.Else) {
                context.consumeToken()
                if (seenElse) {
                    raise(buildError("single else branch", context.top()))
                }
                seenElse = true
                null
            } else {
                if (seenElse) {
                    raise(buildError("end of when after else branch", context.top()))
                }
                parseWith(expressionParser.value, context)
            }

            match<Token.Operator.Arrow>(context.consumeToken()) { buildError("->", it) }
            val block = parseWith(blockParser.value, context)
            branches += WhenBranchAstNode(condition, block)

            if (seenElse && context.top() !is Token.Punctuation.RBrace) {
                raise(buildError("end of when after else branch", context.top()))
            }
        }

        if (branches.isEmpty()) {
            raise(ASTError("Expected at least one when branch at ${context.top().position}"))
        }

        match<Token.Punctuation.RBrace>(context.consumeToken()) { buildError("}", it) }
        return WhenExpressionASTNode(subject, branches)
    }
}
