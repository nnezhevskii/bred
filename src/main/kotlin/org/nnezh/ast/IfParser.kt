package org.nnezh.org.nnezh.ast

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.raise.Raise
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.EmptyNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.IfStatementASTNode
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.AstErrorFactory.buildError

class IfParser(
    private val expressionParser: Parser<ExpressionASTNode>,
    private val blockParser: Lazy<Parser<BlockASTNode>>,
) : Parser<IfStatementASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): IfStatementASTNode {
        match<Token.Keyword.If>(context.consumeToken()) { buildError("if", it) }
        match<Token.Punctuation.LParen>(context.consumeToken()) { buildError("(", it) }
        val condition: ExpressionASTNode = parseWith(expressionParser, context)
        match<Token.Punctuation.RParen>(context.consumeToken()) { buildError(")", it) }
        val thenSection = parseWith(blockParser.value, context)

        val elseBlock: Either<BlockASTNode, EmptyNode> = if (context.top() is Token.Keyword.Else) {
            context.consumeToken()
            parseWith(blockParser.value, context).left()
        } else {
            EmptyNode().right()
        }

        return IfStatementASTNode(
            condition = condition,
            thenBlock = thenSection,
            elseBlock = elseBlock
        )
    }
}
