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
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.Position
import org.nnezh.lexer.Token

class AssignParserTest {

    private val pos = Position(1, 1)

    private fun identifier(name: String) = Token.Identifier(name, pos)

    private fun assign() = Token.Operator.Assign(pos)

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

    private fun parseAssign(
        tokens: List<Token>,
        expressionParser: Parser<ExpressionASTNode> = StubExpressionParser(IntLiteralExpressionNode(0L)),
    ): Either<ASTError, AssignmentStatementASTNode> {
        val parser = AssignParser(expressionParser)
        return either { with(parser) { parse(TokensContext(tokens)) } }
    }

    private fun parseFromSource(src: String): Either<ASTError, AssignmentStatementASTNode> {
        val tokens = Lexer(src).tokenize().getOrElse { error("lexer error: $it") }
        return parseAssign(tokens, AbstractSyntaxTreeExpressionParser())
    }

    // region Positive scenarios

    @Test
    fun `parses variable name and delegates expression to nested parser`() {
        val expression = IntLiteralExpressionNode(99L)
        val result = parseAssign(
            listOf(identifier("x"), assign(), eof()),
            StubExpressionParser(expression),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals("x", result.name)
        assertEquals(expression, result.value)
    }

    @Test
    fun `accepts underscore-prefixed identifier`() {
        val result = parseAssign(
            listOf(identifier("_count"), assign(), eof()),
            StubExpressionParser(IntLiteralExpressionNode(1L)),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals("_count", result.name)
    }

    @Test
    fun `passes unconsumed tokens to expression parser`() {
        val valueToken = Token.Literal.IntLiteral(42L, "42", pos)
        var tokenAtExpressionStart: Token? = null

        parseAssign(
            listOf(identifier("total"), assign(), valueToken, eof()),
            StubExpressionParser(
                result = IntLiteralExpressionNode(42L),
                onParse = { tokenAtExpressionStart = it.top() },
            ),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(valueToken, tokenAtExpressionStart)
    }

    @Test
    fun `parses assignment with literal expression`() {
        val result = parseFromSource("counter = 42").getOrElse { error("unexpected parse error: $it") }

        assertEquals("counter", result.name)
        assertInstanceOf(IntLiteralExpressionNode::class.java, result.value)
        assertEquals(42L, (result.value as IntLiteralExpressionNode).value)
    }

    @Test
    fun `parses assignment with complex expression`() {
        val result = parseFromSource("x = a + b * 2").getOrElse { error("unexpected parse error: $it") }

        assertEquals("x", result.name)
        assertInstanceOf(org.nnezh.ast.BinaryExpressionASTNode::class.java, result.value)
    }

    @Test
    fun `parses assignment with variable expression`() {
        val result = parseFromSource("y = other").getOrElse { error("unexpected parse error: $it") }

        assertEquals("y", result.name)
        assertInstanceOf(VariableExpressionNode::class.java, result.value)
        assertEquals("other", (result.value as VariableExpressionNode).token.lexeme)
    }

    // endregion

    // region Negative scenarios

    @Test
    fun `empty input fails`() {
        assertTrue(parseAssign(listOf(eof())).isLeft())
    }

    @Test
    fun `missing variable name fails`() {
        val result = parseAssign(listOf(assign(), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("variable name") == true)
    }

    @Test
    fun `keyword instead of variable name fails`() {
        val result = parseAssign(listOf(Token.Keyword.If(pos), assign(), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("variable name") == true)
    }

    @Test
    fun `literal instead of variable name fails`() {
        val result = parseAssign(
            listOf(Token.Literal.IntLiteral(1L, "1", pos), assign(), eof()),
        )
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("variable name") == true)
    }

    @Test
    fun `identifier without assign operator fails`() {
        val result = parseAssign(listOf(identifier("x"), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("assignation") == true)
    }

    @Test
    fun `equality operator instead of assign fails`() {
        val result = parseAssign(
            listOf(identifier("x"), Token.Operator.Eq(pos), eof()),
        )
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("assignation") == true)
    }

    @Test
    fun `missing expression fails`() {
        assertTrue(parseFromSource("x =").isLeft())
    }

    @Test
    fun `malformed expression fails`() {
        assertTrue(parseFromSource("x = 3 +").isLeft())
    }

    // endregion
}
