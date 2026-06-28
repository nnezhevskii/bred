/*
 * Shared parser test utilities — stubs and token helpers for unit tests.
 */

package org.nnezh.org.nnezh.ast

import arrow.core.raise.Raise
import org.nnezh.bred.ast.BlockASTNode
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.lexer.Position
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.AstErrorFactory.buildError
import org.nnezh.org.nnezh.ast.parsers.Parser

object ParserTestSupport {
    val testPos: Position = Position(1, 1)

    fun eof() = Token.Eof(testPos)

    fun valKeyword() = Token.Keyword.Val(testPos)

    fun funKeyword() = Token.Keyword.Fun(testPos)

    fun identifier(name: String) = Token.Identifier(name, testPos)

    class StubBlockParser : Parser<BlockASTNode> {
        override fun Raise<ASTError>.parse(context: TokensContext): BlockASTNode {
            match<Token.Punctuation.LBrace>(context.consumeToken()) { buildError("{", it) }
            match<Token.Punctuation.RBrace>(context.consumeToken()) { buildError("}", it) }
            return BlockASTNode(emptyList())
        }
    }

    class StubExpressionParser(
        private val result: ExpressionASTNode,
        private val onParse: ((TokensContext) -> Unit)? = null,
    ) : Parser<ExpressionASTNode> {
        override fun Raise<ASTError>.parse(context: TokensContext): ExpressionASTNode {
            onParse?.invoke(context)
            return result
        }
    }
}
