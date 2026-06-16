package org.nnezh.org.nnezh.ast

import arrow.core.raise.Raise
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.WhileStatementASTNode
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.AstErrorFactory.buildError

class WhileParser(
    private val expressionParser: Parser<ExpressionASTNode>,
    private val blockParser: Lazy<Parser<BlockASTNode>>,
) : Parser<WhileStatementASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): WhileStatementASTNode {
        match<Token.Keyword.While>(context.consumeToken()) { buildError("while", it) }
        match<Token.Punctuation.LParen>(context.consumeToken()) { buildError("(", it) }
        val condition = parseWith(expressionParser, context)
        match<Token.Punctuation.RParen>(context.consumeToken()) { buildError(")", it) }
        val block = parseWith(blockParser.value, context)

        return WhileStatementASTNode(condition, block)
    }
}
