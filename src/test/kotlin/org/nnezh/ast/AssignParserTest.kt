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
import org.nnezh.ast.ArrayAccessExpressionASTNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.ast.lvalueName
import org.nnezh.ast.arrayAccessLValue
import org.nnezh.ast.varExpr
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.Position
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.parsers.AssignParser
import org.nnezh.org.nnezh.ast.parsers.Parser

class AssignParserTest {

    private val pos = Position(1, 1)

    private fun identifier(name: String) = Token.Identifier(name, pos)

    private fun assign() = Token.Operator.Assign(pos)

    private fun eof() = Token.Eof(pos)

    private class SequentialStubExpressionParser(
        private val results: List<ExpressionASTNode>,
        private val onParse: ((Int, TokensContext) -> Unit)? = null,
    ) : Parser<ExpressionASTNode> {
        private var callIndex = 0

        override fun Raise<ASTError>.parse(context: TokensContext): ExpressionASTNode {
            onParse?.invoke(callIndex, context)
            if (!context.endOfInput) {
                context.consumeToken()
            }
            return results[callIndex++]
        }
    }

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
        expressionParser: Parser<ExpressionASTNode> = SequentialStubExpressionParser(
            listOf(varExpr("stub"), IntLiteralExpressionNode(0L)),
        ),
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
        val lValue = varExpr("x")
        val rValue = IntLiteralExpressionNode(99L)
        val result = parseAssign(
            listOf(identifier("x"), assign(), eof()),
            SequentialStubExpressionParser(listOf(lValue, rValue)),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals("x", result.lvalueName())
        assertEquals(rValue, result.rValue)
    }

    @Test
    fun `accepts underscore-prefixed identifier`() {
        val result = parseAssign(
            listOf(identifier("_count"), assign(), eof()),
            SequentialStubExpressionParser(listOf(varExpr("_count"), IntLiteralExpressionNode(1L))),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals("_count", result.lvalueName())
    }

    @Test
    fun `passes unconsumed tokens to expression parser`() {
        val valueToken = Token.Literal.IntLiteral(42L, "42", pos)
        var tokenAtExpressionStart: Token? = null

        parseAssign(
            listOf(identifier("total"), assign(), valueToken, eof()),
            SequentialStubExpressionParser(
                results = listOf(varExpr("total"), IntLiteralExpressionNode(42L)),
                onParse = { index, context -> if (index == 1) tokenAtExpressionStart = context.top() },
            ),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(valueToken, tokenAtExpressionStart)
    }

    @Test
    fun `parses assignment with literal expression`() {
        val result = parseFromSource("counter = 42").getOrElse { error("unexpected parse error: $it") }

        assertEquals("counter", result.lvalueName())
        assertInstanceOf(IntLiteralExpressionNode::class.java, result.rValue)
        assertEquals(42L, (result.rValue as IntLiteralExpressionNode).value)
    }

    @Test
    fun `parses assignment with complex expression`() {
        val result = parseFromSource("x = a + b * 2").getOrElse { error("unexpected parse error: $it") }

        assertEquals("x", result.lvalueName())
        assertInstanceOf(org.nnezh.ast.BinaryExpressionASTNode::class.java, result.rValue)
    }

    @Test
    fun `parses assignment with variable expression`() {
        val result = parseFromSource("y = other").getOrElse { error("unexpected parse error: $it") }

        assertEquals("y", result.lvalueName())
        assertInstanceOf(VariableExpressionNode::class.java, result.rValue)
        assertEquals("other", (result.rValue as VariableExpressionNode).token.lexeme)
    }

    // region Arrays

    @Test
    fun `parses assignment with array access lvalue`() {
        val result = parseFromSource("arr[i] = 1").getOrElse { error("unexpected parse error: $it") }

        val access = result.arrayAccessLValue()
        assertEquals("arr", access.array)
        assertInstanceOf(VariableExpressionNode::class.java, access.index)
        assertEquals("i", (access.index as VariableExpressionNode).token.lexeme)
        assertEquals(1L, (result.rValue as IntLiteralExpressionNode).value)
    }

    @Test
    fun `parses assignment between array accesses`() {
        val result = parseFromSource("arr[0] = arr[1]").getOrElse { error("unexpected parse error: $it") }

        val lValue = result.arrayAccessLValue()
        assertEquals("arr", lValue.array)
        assertEquals(0L, (lValue.index as IntLiteralExpressionNode).value)

        val rValue = assertInstanceOf(ArrayAccessExpressionASTNode::class.java, result.rValue)
        assertEquals("arr", rValue.array)
        assertEquals(1L, (rValue.index as IntLiteralExpressionNode).value)
    }

    // endregion

    // endregion

    // region Negative scenarios

    @Test
    fun `empty input fails`() {
        assertTrue(parseAssign(listOf(eof())).isLeft())
    }

    @Test
    fun `missing lvalue expression fails`() {
        assertTrue(parseFromSource("= 1").isLeft())
    }

    @Test
    fun `keyword instead of variable name fails`() {
        assertTrue(parseFromSource("if = 1").isLeft())
    }

    @Test
    fun `literal lvalue is accepted syntactically`() {
        val result = parseFromSource("1 = 2").getOrElse { error("unexpected parse error: $it") }
        assertInstanceOf(IntLiteralExpressionNode::class.java, result.lValue)
        assertEquals(2L, (result.rValue as IntLiteralExpressionNode).value)
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
