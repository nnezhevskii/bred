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
import org.nnezh.ast.BinaryExpressionASTNode
import org.nnezh.ast.BooleanLiteralExpressionNode
import org.nnezh.ast.DoubleLiteralExpressionNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.ImmutableVariableInitializationASTNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.StringLiteralExpressionNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.Position
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.Type

class ImmutableInitializationParserTest {

    private val pos = Position(1, 1)

    private fun valKeyword() = Token.Keyword.Val(pos)

    private fun identifier(name: String) = Token.Identifier(name, pos)

    private fun colon() = Token.Punctuation.Colon(pos)

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

    private fun parseInit(
        tokens: List<Token>,
        expressionParser: Parser<ExpressionASTNode> = StubExpressionParser(IntLiteralExpressionNode(0L)),
    ): Either<ASTError, ImmutableVariableInitializationASTNode> {
        val parser = ImmutableInitializationParser(expressionParser)
        return either { with(parser) { parse(TokensContext(tokens)) } }
    }

    private fun parseFromSource(src: String): Either<ASTError, ImmutableVariableInitializationASTNode> {
        val tokens = Lexer(src).tokenize().getOrElse { error("lexer error: $it") }
        return parseInit(tokens, AbstractSyntaxTreeExpressionParser())
    }

    private fun initTokens(
        name: String,
        typeName: String,
        vararg tail: Token,
    ): List<Token> = listOf(
        valKeyword(),
        identifier(name),
        colon(),
        identifier(typeName),
        assign(),
        *tail,
    )

    // region Positive scenarios

    @Test
    fun `parses name type and delegates expression to nested parser`() {
        val expression = IntLiteralExpressionNode(99L)
        val result = parseInit(
            initTokens("x", "Int", eof()),
            StubExpressionParser(expression),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals("x", result.name)
        assertEquals(Type.IntType, result.type)
        assertEquals(expression, result.value)
    }

    @Test
    fun `accepts underscore-prefixed identifier`() {
        val result = parseInit(
            initTokens("_count", "Int", eof()),
            StubExpressionParser(IntLiteralExpressionNode(1L)),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals("_count", result.name)
    }

    @Test
    fun `passes unconsumed tokens to expression parser`() {
        val valueToken = Token.Literal.IntLiteral(42L, "42", pos)
        var tokenAtExpressionStart: Token? = null

        parseInit(
            initTokens("total", "Int", valueToken, eof()),
            StubExpressionParser(
                result = IntLiteralExpressionNode(42L),
                onParse = { tokenAtExpressionStart = it.top() },
            ),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(valueToken, tokenAtExpressionStart)
    }

    @Test
    fun `parses double literal with Double type`() {
        val result = parseFromSource("val Pi: Double = 3.1417")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals("Pi", result.name)
        assertEquals(Type.DoubleType, result.type)
        assertInstanceOf(DoubleLiteralExpressionNode::class.java, result.value)
        assertEquals(3.1417, (result.value as DoubleLiteralExpressionNode).value)
    }

    @Test
    fun `parses boolean literal with Boolean type`() {
        val result = parseFromSource("val flag: Boolean = true")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals("flag", result.name)
        assertEquals(Type.BoolType, result.type)
        assertInstanceOf(BooleanLiteralExpressionNode::class.java, result.value)
        assertEquals(true, (result.value as BooleanLiteralExpressionNode).value)
    }

    @Test
    fun `parses string literal with String type`() {
        val result = parseFromSource("val msg: String = \"hi\"")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals("msg", result.name)
        assertEquals(Type.StringType, result.type)
        assertInstanceOf(StringLiteralExpressionNode::class.java, result.value)
        assertEquals("hi", (result.value as StringLiteralExpressionNode).value)
    }

    @Test
    fun `parses int literal with Int type`() {
        val result = parseFromSource("val n: Int = 42")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals("n", result.name)
        assertEquals(Type.IntType, result.type)
        assertInstanceOf(IntLiteralExpressionNode::class.java, result.value)
        assertEquals(42L, (result.value as IntLiteralExpressionNode).value)
    }

    @Test
    fun `parses initialization with complex expression`() {
        val result = parseFromSource("val x: Int = a + b * 2")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals("x", result.name)
        assertInstanceOf(BinaryExpressionASTNode::class.java, result.value)
    }

    @Test
    fun `parses initialization with variable expression`() {
        val result = parseFromSource("val y: Int = other")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals("y", result.name)
        assertInstanceOf(VariableExpressionNode::class.java, result.value)
        assertEquals("other", (result.value as VariableExpressionNode).token.lexeme)
    }

    @Test
    fun `parses initialization with spaces around punctuation`() {
        val result = parseFromSource("val x : Int = 42")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals("x", result.name)
        assertEquals(Type.IntType, result.type)
        assertEquals(42L, (result.value as IntLiteralExpressionNode).value)
    }

    @Test
    fun `parses all supported types via stub tokens`() {
        val typeCases = listOf(
            "Int" to Type.IntType,
            "String" to Type.StringType,
            "Double" to Type.DoubleType,
            "Boolean" to Type.BoolType,
        )

        for ((typeName, expectedType) in typeCases) {
            val result = parseInit(
                initTokens("v", typeName, eof()),
                StubExpressionParser(IntLiteralExpressionNode(0L)),
            ).getOrElse { error("unexpected parse error for $typeName: $it") }

            assertEquals(expectedType, result.type, "type mismatch for $typeName")
        }
    }

    @Test
    fun `accepts long identifier name`() {
        val result = parseInit(
            initTokens("my_var_2", "Int", eof()),
            StubExpressionParser(IntLiteralExpressionNode(0L)),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals("my_var_2", result.name)
    }

    // endregion

    // region Negative scenarios

    @Test
    fun `empty input fails`() {
        assertTrue(parseInit(listOf(eof())).isLeft())
    }

    @Test
    fun `missing val keyword fails`() {
        val result = parseInit(listOf(identifier("x"), colon(), identifier("Int"), assign(), eof()))
        assertTrue(result.isLeft())
    }

    @Test
    fun `fun instead of val fails`() {
        val result = parseInit(
            listOf(Token.Keyword.Fun(pos), identifier("x"), colon(), identifier("Int"), assign(), eof()),
        )
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected val") == true)
    }

    @Test
    fun `if instead of val fails`() {
        val result = parseInit(
            listOf(Token.Keyword.If(pos), identifier("x"), colon(), identifier("Int"), assign(), eof()),
        )
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected val") == true)
    }

    @Test
    fun `val without name fails`() {
        val result = parseInit(listOf(valKeyword(), colon(), identifier("Int"), assign(), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("variable name") == true)
    }

    @Test
    fun `keyword instead of variable name fails`() {
        val result = parseFromSource("val if : Int = 1")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("variable name") == true)
    }

    @Test
    fun `literal instead of variable name fails`() {
        val result = parseFromSource("val 42 : Int = 1")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("variable name") == true)
    }

    @Test
    fun `missing colon fails`() {
        val result = parseFromSource("val x Int = 1")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains(":") == true)
    }

    @Test
    fun `semicolon instead of colon fails`() {
        val result = parseInit(
            listOf(
                valKeyword(),
                identifier("x"),
                Token.Punctuation.Semicolon(pos),
                identifier("Int"),
                assign(),
                eof(),
            ),
        )
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains(":") == true)
    }

    @Test
    fun `missing type fails`() {
        val result = parseFromSource("val x : = 1")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("variable type") == true)
    }

    @Test
    fun `keyword instead of type fails`() {
        val result = parseFromSource("val x : if = 1")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("variable type") == true)
    }

    @Test
    fun `literal instead of type fails`() {
        val result = parseFromSource("val x : 42 = 1")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("variable type") == true)
    }

    @Test
    fun `unknown type fails`() {
        val result = parseFromSource("val x : Foo = 1")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Invalid type Foo") == true)
    }

    @Test
    fun `missing assign operator fails`() {
        val result = parseFromSource("val x : Int")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("assignation") == true)
    }

    @Test
    fun `equality operator instead of assign fails`() {
        val result = parseFromSource("val x : Int == 1")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("assignation") == true)
    }

    @Test
    fun `missing expression fails`() {
        assertTrue(parseFromSource("val x : Int =").isLeft())
    }

    @Test
    fun `malformed expression fails`() {
        assertTrue(parseFromSource("val x : Int = 3 +").isLeft())
    }

    @Test
    fun `only val keyword fails`() {
        assertTrue(parseFromSource("val").isLeft())
    }

    @Test
    fun `val and name only fails`() {
        assertTrue(parseFromSource("val x").isLeft())
    }

    @Test
    fun `val name and colon only fails`() {
        assertTrue(parseFromSource("val x :").isLeft())
    }

    @Test
    fun `val name colon and type only fails`() {
        assertTrue(parseFromSource("val x : Int").isLeft())
    }

    // endregion
}
