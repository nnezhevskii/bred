/*
 * This file was generated with the assistance of AI (Cursor).
 */

package org.nnezh.org.nnezh.ast

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.ast.AssignmentStatementASTNode
import org.nnezh.ast.BinaryExpressionASTNode
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.BooleanLiteralExpressionNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.FunctionCallExpressionNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.StatementASTNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.ast.WhileStatementASTNode
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.Position
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.AstErrorFactory.buildError

class WhileParserTest {

    private val pos = Position(1, 1)

    private fun whileKeyword() = Token.Keyword.While(pos)

    private fun lbrace() = Token.Punctuation.LBrace(pos)

    private fun rbrace() = Token.Punctuation.RBrace(pos)

    private fun identifier(name: String) = Token.Identifier(name, pos)

    private fun eof() = Token.Eof(pos)

    private class StubExpressionParser(
        private val result: ExpressionASTNode,
        private val onParse: ((TokensContext) -> Unit)? = null,
    ) : Parser<ExpressionASTNode> {
        override fun Raise<ASTError>.parse(context: TokensContext): ExpressionASTNode {
            onParse?.invoke(context)
            return result
        }
    }

    private class StubBlockParser(
        private val block: BlockASTNode = BlockASTNode(emptyList()),
        private val onParse: ((TokensContext) -> Unit)? = null,
    ) : Parser<BlockASTNode> {
        override fun Raise<ASTError>.parse(context: TokensContext): BlockASTNode {
            onParse?.invoke(context)
            match<Token.Punctuation.LBrace>(context.consumeToken()) { buildError("{", it) }
            match<Token.Punctuation.RBrace>(context.consumeToken()) { buildError("}", it) }
            return block
        }
    }

    private fun parseWhile(
        tokens: List<Token>,
        expressionParser: Parser<ExpressionASTNode> = StubExpressionParser(IntLiteralExpressionNode(0L)),
        blockParser: Parser<BlockASTNode> = StubBlockParser(),
    ): Either<ASTError, WhileStatementASTNode> {
        val parser = WhileParser(expressionParser, lazy { blockParser })
        return either { with(parser) { parse(TokensContext(tokens)) } }
    }

    private fun parseFromSource(
        src: String,
        blockParser: Parser<BlockASTNode> = StubBlockParser(),
    ): Either<ASTError, WhileStatementASTNode> {
        val tokens = Lexer(src).tokenize().getOrElse { error("lexer error: $it") }
        return parseWhile(tokens, AbstractSyntaxTreeExpressionParser(), blockParser)
    }

    // region Positive scenarios

    @Test
    fun `parses while keyword and delegates condition and block to nested parsers`() {
        val condition = IntLiteralExpressionNode(1L)
        val block = BlockASTNode(emptyList())
        val result = parseWhile(
            listOf(whileKeyword(), lbrace(), rbrace(), eof()),
            StubExpressionParser(condition),
            StubBlockParser(block),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(condition, result.condition)
        assertEquals(block, result.bodyBlock)
    }

    @Test
    fun `passes first token after while to expression parser`() {
        val conditionToken = identifier("x")
        var tokenAtExpressionStart: Token? = null

        val consumingStub = object : Parser<ExpressionASTNode> {
            override fun Raise<ASTError>.parse(context: TokensContext): ExpressionASTNode {
                tokenAtExpressionStart = context.top()
                context.consumeToken()
                return VariableExpressionNode(conditionToken)
            }
        }

        parseWhile(
            listOf(whileKeyword(), conditionToken, lbrace(), rbrace(), eof()),
            consumingStub,
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(conditionToken, tokenAtExpressionStart)
    }

    @Test
    fun `passes context after condition to block parser`() {
        var tokenAtBlockStart: Token? = null

        parseFromSource(
            "while x > 0 { }",
            StubBlockParser(onParse = { tokenAtBlockStart = it.top() }),
        ).getOrElse { error("unexpected parse error: $it") }

        assertInstanceOf(Token.Punctuation.LBrace::class.java, tokenAtBlockStart)
    }

    @Test
    fun `parses while with comparison condition via lexer`() {
        val result = parseFromSource("while x > 0 { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertInstanceOf(BinaryExpressionASTNode::class.java, result.condition)
        val binary = result.condition as BinaryExpressionASTNode
        assertInstanceOf(VariableExpressionNode::class.java, binary.left)
        assertInstanceOf(Token.Operator.Gt::class.java, binary.operator)
        assertInstanceOf(IntLiteralExpressionNode::class.java, binary.right)
    }

    @Test
    fun `parses while with parenthesized function call condition`() {
        val result = parseFromSource("while (loop() > 1) { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertInstanceOf(BinaryExpressionASTNode::class.java, result.condition)
        val binary = result.condition as BinaryExpressionASTNode
        assertInstanceOf(FunctionCallExpressionNode::class.java, binary.left)
        assertEquals("loop", (binary.left as FunctionCallExpressionNode).name.lexeme)
    }

    @Test
    fun `parses while with complex condition expression`() {
        val result = parseFromSource("while a + b * 2 > 0 { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertInstanceOf(BinaryExpressionASTNode::class.java, result.condition)
    }

    @Test
    fun `parses while with empty block`() {
        val result = parseFromSource("while x > 0 { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertTrue(result.bodyBlock.statements.isEmpty())
    }

    @Test
    fun `uses block returned by block parser`() {
        val customStatement: StatementASTNode =
            AssignmentStatementASTNode("a", IntLiteralExpressionNode(1L))
        val customBlock = BlockASTNode(listOf(customStatement))

        val result = parseWhile(
            listOf(whileKeyword(), lbrace(), rbrace(), eof()),
            StubExpressionParser(IntLiteralExpressionNode(0L)),
            StubBlockParser(customBlock),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(customBlock, result.bodyBlock)
    }

    @Test
    fun `parses while with extra spaces via lexer`() {
        val result = parseFromSource("while  x  >  0  {  }")
            .getOrElse { error("unexpected parse error: $it") }

        assertInstanceOf(BinaryExpressionASTNode::class.java, result.condition)
        assertTrue(result.bodyBlock.statements.isEmpty())
    }

    @Test
    fun `parses while with boolean literal condition`() {
        val result = parseFromSource("while true { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertInstanceOf(BooleanLiteralExpressionNode::class.java, result.condition)
        assertEquals(true, (result.condition as BooleanLiteralExpressionNode).value)
    }

    // endregion

    // region Negative scenarios

    @Test
    fun `empty input fails`() {
        assertTrue(parseWhile(listOf(eof())).isLeft())
    }

    @Test
    fun `missing while keyword fails`() {
        assertTrue(parseWhile(listOf(identifier("x"), lbrace(), rbrace(), eof())).isLeft())
    }

    @Test
    fun `if instead of while fails`() {
        val result = parseWhile(listOf(Token.Keyword.If(pos), lbrace(), rbrace(), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("while") == true)
    }

    @Test
    fun `for instead of while fails`() {
        val result = parseWhile(listOf(Token.Keyword.For(pos), lbrace(), rbrace(), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("while") == true)
    }

    @Test
    fun `val instead of while fails`() {
        val result = parseWhile(listOf(Token.Keyword.Val(pos), lbrace(), rbrace(), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("while") == true)
    }

    @Test
    fun `only while keyword fails`() {
        assertTrue(parseWhile(listOf(whileKeyword(), eof())).isLeft())
    }

    @Test
    fun `while followed by eof fails`() {
        assertTrue(parseFromSource("while").isLeft())
    }

    @Test
    fun `malformed condition fails`() {
        assertTrue(parseFromSource("while x >").isLeft())
    }

    @Test
    fun `missing block fails`() {
        assertTrue(parseFromSource("while x > 0").isLeft())
    }

    @Test
    fun `lparen instead of block fails`() {
        val result = parseFromSource("while x > 0 (")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("{") == true)
    }

    @Test
    fun `unclosed block fails`() {
        assertTrue(parseFromSource("while x > 0 {").isLeft())
    }

    @Test
    fun `rbrace instead of lbrace fails`() {
        val result = parseFromSource("while x > 0 }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("{") == true)
    }

    @Test
    fun `literal instead of while fails`() {
        val result = parseWhile(
            listOf(Token.Literal.IntLiteral(1L, "1", pos), lbrace(), rbrace(), eof()),
        )
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("while") == true)
    }

    @Test
    fun `empty parenthesized condition fails`() {
        assertTrue(parseFromSource("while () { }").isLeft())
    }

    // endregion
}
