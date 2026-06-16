/*
 * This file was generated with the assistance of AI (Cursor).
 */

package org.nnezh.org.nnezh.ast

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.Raise
import arrow.core.raise.either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.ast.AssignmentStatementASTNode
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.DeclareFunctionASTNode
import org.nnezh.ast.FunctionArgumentASTNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.ReturnFunctionStatementASTNode
import org.nnezh.ast.StatementASTNode
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.Position
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.Type
import org.nnezh.org.nnezh.ast.AstErrorFactory.buildError

class FunctionParserTest {

    private val pos = Position(1, 1)

    private fun funKeyword() = Token.Keyword.Fun(pos)

    private fun lparen() = Token.Punctuation.LParen(pos)

    private fun rparen() = Token.Punctuation.RParen(pos)

    private fun colon() = Token.Punctuation.Colon(pos)

    private fun comma() = Token.Punctuation.Comma(pos)

    private fun lbrace() = Token.Punctuation.LBrace(pos)

    private fun rbrace() = Token.Punctuation.RBrace(pos)

    private fun identifier(name: String) = Token.Identifier(name, pos)

    private fun eof() = Token.Eof(pos)

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

    private fun parseFunction(
        tokens: List<Token>,
        blockParser: Parser<BlockASTNode> = StubBlockParser(),
    ): Either<ASTError, DeclareFunctionASTNode> {
        val parser = FunctionParser(lazy { blockParser })
        return either { with(parser) { parse(TokensContext(tokens)) } }
    }

    private fun parseFromSource(
        src: String,
        blockParser: Parser<BlockASTNode> = StubBlockParser(),
    ): Either<ASTError, DeclareFunctionASTNode> {
        val tokens = Lexer(src).tokenize().getOrElse { error("lexer error: $it") }
        return parseFunction(tokens, blockParser)
    }

    private fun assertImplicitUnitReturn(statement: StatementASTNode) {
        val returnStmt = assertInstanceOf(ReturnFunctionStatementASTNode::class.java, statement)
        assertTrue(returnStmt.expression.isLeft())
        assertEquals(Type.UnitType, returnStmt.expression.leftOrNull())
    }

    // region Positive scenarios

    @Test
    fun `parses function without arguments`() {
        val result = parseFromSource("fun foo(): Unit { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals("foo", result.name)
        assertTrue(result.args.arguments.isEmpty())
        assertEquals(Type.UnitType, result.resultType)
        assertEquals(1, result.block.statements.size)
        assertImplicitUnitReturn(result.block.statements.single())
    }

    @Test
    fun `parses function with two typed arguments`() {
        val result = parseFromSource("fun min(a: Int, b: String): Unit { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals("min", result.name)
        assertEquals(2, result.args.arguments.size)
        assertEquals(FunctionArgumentASTNode("a", Type.IntType), result.args.arguments[0])
        assertEquals(FunctionArgumentASTNode("b", Type.StringType), result.args.arguments[1])
        assertEquals(Type.UnitType, result.resultType)
    }

    @Test
    fun `parses function with Int return type`() {
        val result = parseFromSource("fun max(a: Int, b: Int): Int { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals("max", result.name)
        assertEquals(Type.IntType, result.resultType)
        assertEquals(Type.IntType, result.args.arguments[0].type)
    }

    @Test
    fun `parses function with three arguments`() {
        val result = parseFromSource("fun f(x: Int, y: Double, z: Boolean): Unit { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals(3, result.args.arguments.size)
        assertEquals(Type.DoubleType, result.args.arguments[1].type)
        assertEquals(Type.BoolType, result.args.arguments[2].type)
    }

    @Test
    fun `delegates body to block parser`() {
        val customStatement: StatementASTNode =
            AssignmentStatementASTNode("a", IntLiteralExpressionNode(1L))
        val customBlock = BlockASTNode(listOf(customStatement))

        val result = parseFromSource("fun foo(): Unit { }", StubBlockParser(customBlock))
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals(2, result.block.statements.size)
        assertEquals(customStatement, result.block.statements[0])
        assertImplicitUnitReturn(result.block.statements[1])
    }

    @Test
    fun `passes lbrace to block parser after return type`() {
        var tokenAtBlockStart: Token? = null

        parseFromSource(
            "fun foo(): Unit { }",
            StubBlockParser(onParse = { tokenAtBlockStart = it.top() }),
        ).getOrElse { error("unexpected parse error: $it") }

        assertInstanceOf(Token.Punctuation.LBrace::class.java, tokenAtBlockStart)
    }

    @Test
    fun `accepts underscore-prefixed function name`() {
        val result = parseFromSource("fun _helper(): Unit { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals("_helper", result.name)
    }

    @Test
    fun `parses function with extra spaces via lexer`() {
        val result = parseFromSource("fun  foo  (  a  :  Int  )  :  Unit  {  }")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals("foo", result.name)
        assertEquals(1, result.args.arguments.size)
        assertEquals(Type.UnitType, result.resultType)
    }

    @Test
    fun `parses function with single argument`() {
        val result = parseFromSource("fun read(x: String): Int { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals(1, result.args.arguments.size)
        assertEquals("x", result.args.arguments.single().name)
        assertEquals(Type.StringType, result.args.arguments.single().type)
    }

    @Test
    fun `parses function with implicit Unit return type`() {
        val result = parseFromSource("fun main() { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals("main", result.name)
        assertEquals(Type.UnitType, result.resultType)
    }

    @Test
    fun `parses all supported argument types`() {
        val result = parseFromSource("fun types(i: Int, s: String, d: Double, b: Boolean): Unit { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals(Type.IntType, result.args.arguments[0].type)
        assertEquals(Type.StringType, result.args.arguments[1].type)
        assertEquals(Type.DoubleType, result.args.arguments[2].type)
        assertEquals(Type.BoolType, result.args.arguments[3].type)
    }

    @Test
    fun `appends implicit return Unit to empty Unit function body`() {
        val result = parseFromSource("fun main(): Unit { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals(1, result.block.statements.size)
        assertImplicitUnitReturn(result.block.statements.single())
    }

    @Test
    fun `does not append implicit return when top-level return exists`() {
        val returnStmt = ReturnFunctionStatementASTNode(Type.UnitType.left())
        val blockWithReturn = BlockASTNode(listOf(returnStmt))

        val result = parseFromSource("fun f(): Unit { }", StubBlockParser(blockWithReturn))
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals(1, result.block.statements.size)
        assertEquals(returnStmt, result.block.statements.single())
    }

    @Test
    fun `appends implicit return Unit even for Int function without return`() {
        val result = parseFromSource("fun f(): Int { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals(Type.IntType, result.resultType)
        assertEquals(1, result.block.statements.size)
        assertImplicitUnitReturn(result.block.statements.single())
    }

    // endregion

    // region Negative scenarios

    @Test
    fun `empty input fails`() {
        assertTrue(parseFunction(listOf(eof())).isLeft())
    }

    @Test
    fun `missing fun keyword fails`() {
        assertTrue(parseFunction(listOf(identifier("foo"), lparen(), rparen(), colon(), identifier("Unit"), lbrace(), rbrace(), eof())).isLeft())
    }

    @Test
    fun `val instead of fun fails`() {
        val result = parseFunction(listOf(Token.Keyword.Val(pos), identifier("foo"), lparen(), rparen(), colon(), identifier("Unit"), lbrace(), rbrace(), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected fun") == true)
    }

    @Test
    fun `only fun keyword fails`() {
        assertTrue(parseFunction(listOf(funKeyword(), eof())).isLeft())
    }

    @Test
    fun `fun without name fails`() {
        val result = parseFunction(listOf(funKeyword(), lparen(), rparen(), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected fun name") == true)
    }

    @Test
    fun `keyword instead of function name fails`() {
        val result = parseFromSource("fun if(): Unit { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected fun name") == true)
    }

    @Test
    fun `fun without lparen fails`() {
        val result = parseFromSource("fun foo: Unit { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected (") == true)
    }

    @Test
    fun `unclosed argument list fails`() {
        val result = parseFromSource("fun foo(a: Int")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Unexpected EOF") == true)
    }

    @Test
    fun `argument without colon fails`() {
        val result = parseFromSource("fun foo(a Int): Unit { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected colon") == true)
    }

    @Test
    fun `argument without type fails`() {
        val result = parseFromSource("fun foo(a:): Unit { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected argument type") == true)
    }

    @Test
    fun `missing rparen after arguments fails`() {
        val result = parseFromSource("fun foo(a: Int: Unit { }")
        assertTrue(result.isLeft())
    }

    @Test
    fun `return type without colon fails`() {
        val result = parseFromSource("fun foo() Unit { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected ':' before return type") == true)
    }

    @Test
    fun `missing return type fails`() {
        val result = parseFromSource("fun foo(): { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected return type") == true)
    }

    @Test
    fun `leading comma in argument list fails`() {
        val result = parseFromSource("fun foo(,a: Int): Unit { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Didn't expect comma") == true)
    }

    @Test
    fun `trailing comma in argument list fails`() {
        val result = parseFromSource("fun foo(a: Int,): Unit { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected next argument") == true)
    }

    @Test
    fun `double comma in argument list fails`() {
        val result = parseFromSource("fun foo(a: Int,, b: Int): Unit { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected next argument") == true)
    }

    @Test
    fun `literal instead of fun fails`() {
        val result = parseFunction(
            listOf(Token.Literal.IntLiteral(1L, "1", pos), identifier("foo"), lparen(), rparen(), colon(), identifier("Unit"), lbrace(), rbrace(), eof()),
        )
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected fun") == true)
    }

    @Test
    fun `missing block fails`() {
        assertTrue(parseFromSource("fun foo(): Unit").isLeft())
    }

    @Test
    fun `unclosed block fails`() {
        assertTrue(parseFromSource("fun foo(): Unit {").isLeft())
    }

    @Test
    fun `unknown return type fails with parse error`() {
        val result = parseFromSource("fun f(): Foo { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Unexpected type Foo") == true)
    }

    @Test
    fun `unknown argument type fails with parse error`() {
        val result = parseFromSource("fun foo(a: Foo): Unit { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Invalid type Foo") == true)
    }

    // endregion
}
