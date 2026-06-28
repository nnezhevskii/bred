/*
 * This file was generated with the assistance of AI (Cursor).
 */

package org.nnezh.org.nnezh.ast

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.bred.ast.ReturnFunctionStatementASTNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.Position
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.parsers.Parser
import org.nnezh.org.nnezh.ast.parsers.ReturnValueParser
import org.nnezh.org.nnezh.base.Type

class ReturnValueParserTest {

    private val pos = Position(1, 1)

    private fun returnKeyword() = Token.Keyword.Return(pos)

    private fun rbrace() = Token.Punctuation.RBrace(pos)

    private fun eof() = Token.Eof(pos)

    private fun identifier(name: String) = Token.Identifier(name, pos)

    private class StubExpressionParser(
        private val result: ExpressionASTNode,
        private val onParse: ((TokensContext) -> Unit)? = null,
    ) : Parser<ExpressionASTNode> {
        var invoked: Boolean = false

        override fun Raise<ASTError>.parse(context: TokensContext): ExpressionASTNode {
            invoked = true
            onParse?.invoke(context)
            return result
        }
    }

    private class FailingExpressionParser(
        private val message: String,
    ) : Parser<ExpressionASTNode> {
        var invoked: Boolean = false

        override fun Raise<ASTError>.parse(context: TokensContext): ExpressionASTNode {
            invoked = true
            raise(ASTError(message))
        }
    }

    private fun parseReturn(
        tokens: List<Token>,
        expressionParser: Parser<ExpressionASTNode> = StubExpressionParser(IntLiteralExpressionNode(0L)),
    ): Either<ASTError, ReturnFunctionStatementASTNode> {
        val parser = ReturnValueParser(expressionParser)
        return either { with(parser) { parse(TokensContext(tokens)) } }
    }

    private fun parseFromSource(src: String): Either<ASTError, ReturnFunctionStatementASTNode> {
        val tokens = Lexer(src).tokenize().getOrElse { error("lexer error: $it") }
        return parseReturn(tokens, AbstractSyntaxTreeExpressionParser())
    }

    private fun assertUnitReturn(result: ReturnFunctionStatementASTNode) {
        assertTrue(result.expression.isLeft(), "expected Unit return (Either.Left)")
        assertEquals(Type.UnitType, result.expression.leftOrNull())
    }

    private fun assertExpressionReturn(result: ReturnFunctionStatementASTNode, expected: ExpressionASTNode) {
        assertTrue(result.expression.isRight(), "expected expression return (Either.Right)")
        assertEquals(expected, result.expression.getOrNull())
    }

    // region Positive scenarios

    @Test
    fun `parses return with int literal expression`() {
        val result = parseFromSource("return 42").getOrElse { error("unexpected parse error: $it") }
        assertExpressionReturn(result, IntLiteralExpressionNode(42L))
    }

    @Test
    fun `parses return with variable expression`() {
        val result = parseFromSource("return x").getOrElse { error("unexpected parse error: $it") }
        assertTrue(result.expression.isRight())
        assertEquals("x", (result.expression.getOrNull() as VariableExpressionNode).token.lexeme)
    }

    @Test
    fun `parses return Unit as UnitType not variable`() {
        val result = parseFromSource("return Unit").getOrElse { error("unexpected parse error: $it") }
        assertUnitReturn(result)
    }

    @Test
    fun `bare return before rbrace is same as return Unit`() {
        val result = parseReturn(listOf(returnKeyword(), rbrace(), eof()))
            .getOrElse { error("unexpected parse error: $it") }
        assertUnitReturn(result)
    }

    @Test
    fun `delegates expression parsing to nested parser`() {
        val expression = IntLiteralExpressionNode(99L)
        val expressionStub = StubExpressionParser(expression)

        val result = parseReturn(
            listOf(returnKeyword(), Token.Literal.IntLiteral(99L, "99", pos), eof()),
            expressionStub,
        ).getOrElse { error("unexpected parse error: $it") }

        assertTrue(expressionStub.invoked)
        assertExpressionReturn(result, expression)
    }

    @Test
    fun `passes token after return to expression parser`() {
        val valueToken = Token.Literal.IntLiteral(7L, "7", pos)
        var tokenAtExpressionStart: Token? = null

        parseReturn(
            listOf(returnKeyword(), valueToken, eof()),
            StubExpressionParser(
                result = IntLiteralExpressionNode(7L),
                onParse = { tokenAtExpressionStart = it.top() },
            ),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(valueToken, tokenAtExpressionStart)
    }

    // endregion

    // region Negative scenarios

    @Test
    fun `return without value before eof fails`() {
        assertTrue(parseReturn(listOf(returnKeyword(), eof())).isLeft())
    }

    @Test
    fun `return Unit does not invoke expression parser`() {
        val expressionStub = StubExpressionParser(IntLiteralExpressionNode(0L))
        parseReturn(
            listOf(returnKeyword(), identifier("Unit"), eof()),
            expressionStub,
        ).getOrElse { error("unexpected parse error: $it") }
        assertFalse(expressionStub.invoked)
    }

    @Test
    fun `bare return before rbrace does not invoke expression parser`() {
        val expressionStub = StubExpressionParser(IntLiteralExpressionNode(0L))
        parseReturn(listOf(returnKeyword(), rbrace(), eof()), expressionStub)
            .getOrElse { error("unexpected parse error: $it") }
        assertFalse(expressionStub.invoked)
    }

    @Test
    fun `propagates expression parser failure`() {
        val failing = FailingExpressionParser("expression failure")
        val result = parseReturn(listOf(returnKeyword(), identifier("x"), eof()), failing)
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("expression failure") == true)
        assertTrue(failing.invoked)
    }

    @Test
    fun `malformed expression after return fails`() {
        assertTrue(parseFromSource("return 3 +").isLeft())
    }

    @Test
    fun `missing return keyword fails`() {
        val result = parseReturn(listOf(identifier("return"), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("return") == true)
    }

    // endregion
}
