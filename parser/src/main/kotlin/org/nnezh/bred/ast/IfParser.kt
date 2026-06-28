package org.nnezh.bred.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.AstErrorFactory.buildError
import org.nnezh.bred.ast.BlockAstNode
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.bred.ast.IfStatementAstNode
import org.nnezh.bred.ast.Parser
import org.nnezh.bred.ast.TokensContext
import org.nnezh.bred.ast.match
import org.nnezh.bred.ast.parseWith
import org.nnezh.lexer.Token

class IfParser(
    private val expressionParser: Parser<ExpressionASTNode>,
    private val blockParser: Lazy<Parser<BlockAstNode>>,
) : Parser<IfStatementAstNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): IfStatementAstNode {
        match<Token.Keyword.If>(context.consumeToken()) { buildError("if", it) }
        match<Token.Punctuation.LParen>(context.consumeToken()) { buildError("(", it) }
        val condition: ExpressionASTNode = parseWith(expressionParser, context)
        match<Token.Punctuation.RParen>(context.consumeToken()) { buildError(")", it) }
        val thenSection = parseWith(blockParser.value, context)

        val elseBlock: BlockAstNode? = if (context.top() is Token.Keyword.Else) {
            context.consumeToken()
            parseWith(blockParser.value, context)
        } else {
            null
        }

        return IfStatementAstNode(
            condition = condition,
            thenBlock = thenSection,
            elseBlock = elseBlock
        )
    }
}
