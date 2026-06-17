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
import org.nnezh.ast.CallFunctionStatementASTNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.FunctionCallExpressionNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.Position
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.parsers.CallStatementParser
import org.nnezh.org.nnezh.ast.parsers.Parser

class CallStatementParserTest {

    private val pos = Position(1, 1)

    private fun identifier(name: String) = Token.Identifier(name, pos)

    private fun lparen() = Token.Punctuation.LParen(pos)

    private fun eof() = Token.Eof(pos)

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

    private fun parseCall(
        tokens: List<Token>,
        expressionParser: Parser<ExpressionASTNode> = StubExpressionParser(
            FunctionCallExpressionNode(identifier("stub"), emptyList()),
        ),
    ): Either<ASTError, CallFunctionStatementASTNode> {
        val parser = CallStatementParser(expressionParser)
        return either { with(parser) { parse(TokensContext(tokens)) } }
    }

    private fun parseFromSource(src: String): Either<ASTError, CallFunctionStatementASTNode> {
        val tokens = Lexer(src).tokenize().getOrElse { error("lexer error: $it") }
        return parseCall(tokens, AbstractSyntaxTreeExpressionParser())
    }

    // region Positive scenarios

    @Test
    fun `delegates parsing to nested expression parser`() {
        val expressionStub = StubExpressionParser(
            FunctionCallExpressionNode(identifier("foo"), emptyList()),
        )

        parseCall(listOf(identifier("foo"), lparen(), eof()), expressionStub)
            .getOrElse { error("unexpected parse error: $it") }

        assertTrue(expressionStub.invoked)
    }

    @Test
    fun `wraps expression result in call statement node`() {
        val expression = FunctionCallExpressionNode(identifier("bar"), listOf(IntLiteralExpressionNode(1L)))
        val result = parseCall(
            listOf(identifier("bar"), lparen(), eof()),
            StubExpressionParser(expression),
        ).getOrElse { error("unexpected parse error: $it") }

        assertInstanceOf(CallFunctionStatementASTNode::class.java, result)
        assertEquals(expression, result.expression)
    }

    @Test
    fun `passes tokens from start to expression parser`() {
        val nameToken = identifier("println")
        var tokenAtExpressionStart: Token? = null

        parseCall(
            listOf(nameToken, lparen(), identifier("x"), Token.Punctuation.RParen(pos), eof()),
            StubExpressionParser(
                result = FunctionCallExpressionNode(nameToken, emptyList()),
                onParse = { tokenAtExpressionStart = it.top() },
            ),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(nameToken, tokenAtExpressionStart)
    }

    @Test
    fun `parses empty function call`() {
        val result = parseFromSource("foo()").getOrElse { error("unexpected parse error: $it") }

        val call = result.expression as FunctionCallExpressionNode
        assertEquals("foo", call.name.lexeme)
        assertTrue(call.arguments.isEmpty())
    }

    @Test
    fun `parses function call with arguments`() {
        val result = parseFromSource("println(a, 42)").getOrElse { error("unexpected parse error: $it") }

        val call = result.expression as FunctionCallExpressionNode
        assertEquals("println", call.name.lexeme)
        assertEquals(2, call.arguments.size)
        assertInstanceOf(VariableExpressionNode::class.java, call.arguments[0])
        assertInstanceOf(IntLiteralExpressionNode::class.java, call.arguments[1])
    }

    @Test
    fun `parses nested function calls`() {
        val result = parseFromSource("max(a, min(b, c))").getOrElse { error("unexpected parse error: $it") }

        val outer = result.expression as FunctionCallExpressionNode
        assertEquals("max", outer.name.lexeme)
        assertEquals(2, outer.arguments.size)

        val inner = outer.arguments[1] as FunctionCallExpressionNode
        assertEquals("min", inner.name.lexeme)
        assertEquals(listOf("b", "c"), inner.arguments.map { (it as VariableExpressionNode).token.lexeme })
    }

    @Test
    fun `parses function call with expression arguments`() {
        val result = parseFromSource("f(a + 1, b * 2)").getOrElse { error("unexpected parse error: $it") }

        val call = result.expression as FunctionCallExpressionNode
        assertEquals("f", call.name.lexeme)
        assertEquals(2, call.arguments.size)
        assertInstanceOf(org.nnezh.ast.BinaryExpressionASTNode::class.java, call.arguments[0])
        assertInstanceOf(org.nnezh.ast.BinaryExpressionASTNode::class.java, call.arguments[1])
    }

    // endregion

    // region Negative scenarios

    @Test
    fun `empty input fails`() {
        assertTrue(parseCall(listOf(eof()), AbstractSyntaxTreeExpressionParser()).isLeft())
    }

    @Test
    fun `propagates expression parser failure`() {
        val failing = FailingExpressionParser("expression failure")
        val result = parseCall(listOf(identifier("foo"), lparen(), eof()), failing)
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("expression failure") == true)
        assertTrue(failing.invoked)
    }

    @Test
    fun `malformed function call fails`() {
        assertTrue(parseFromSource("foo(").isLeft())
    }

    @Test
    fun `truncated call with missing closing paren fails`() {
        assertTrue(parseFromSource("println(a").isLeft())
    }

    @Test
    fun `malformed expression in call fails`() {
        assertTrue(parseFromSource("foo(3 +)").isLeft())
    }

    @Test
    fun `empty argument list with trailing comma fails`() {
        assertTrue(parseFromSource("max(3,)").isLeft())
    }

    // endregion
}
