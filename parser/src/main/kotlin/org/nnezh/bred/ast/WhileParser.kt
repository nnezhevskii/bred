package org.nnezh.bred.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.AstErrorFactory.buildError
import org.nnezh.bred.ast.BlockAstNode
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.bred.ast.Parser
import org.nnezh.bred.ast.TokensContext
import org.nnezh.bred.ast.WhileStatementAstNode
import org.nnezh.bred.ast.match
import org.nnezh.bred.ast.parseWith
import org.nnezh.lexer.Token

class WhileParser(
    private val expressionParser: Parser<ExpressionASTNode>,
    private val blockParser: Lazy<Parser<BlockAstNode>>,
) : Parser<WhileStatementAstNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): WhileStatementAstNode {
        match<Token.Keyword.While>(context.consumeToken()) { buildError("while", it) }
        match<Token.Punctuation.LParen>(context.consumeToken()) { buildError("(", it) }
        val condition = parseWith(expressionParser, context)
        match<Token.Punctuation.RParen>(context.consumeToken()) { buildError(")", it) }
        val block = parseWith(blockParser.value, context)

        return WhileStatementAstNode(condition, block)
    }
}
