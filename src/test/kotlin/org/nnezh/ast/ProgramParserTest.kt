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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.DeclareFunctionASTNode
import org.nnezh.ast.FunctionArgsASTNode
import org.nnezh.ast.ImmutableVariableInitializationASTNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.ProgramASTNode
import org.nnezh.ast.ReturnFunctionStatementASTNode
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.Position
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.Type
import org.nnezh.org.nnezh.ast.AstErrorFactory.buildError

class ProgramParserTest {

    private val pos = Position(1, 1)

    private fun funKeyword() = Token.Keyword.Fun(pos)

    private fun valKeyword() = Token.Keyword.Val(pos)

    private fun eof() = Token.Eof(pos)

    private class TaggingFunctionStubParser(
        val result: DeclareFunctionASTNode,
    ) : Parser<DeclareFunctionASTNode> {
        var invoked: Boolean = false

        override fun Raise<ASTError>.parse(context: TokensContext): DeclareFunctionASTNode {
            invoked = true
            while (!context.endOfInput) {
                context.consumeToken()
            }
            return result
        }
    }

    private class TaggingInitStubParser(
        val result: ImmutableVariableInitializationASTNode,
    ) : Parser<ImmutableVariableInitializationASTNode> {
        var invoked: Boolean = false

        override fun Raise<ASTError>.parse(context: TokensContext): ImmutableVariableInitializationASTNode {
            invoked = true
            while (!context.endOfInput) {
                context.consumeToken()
            }
            return result
        }
    }

    private class FailingFunctionParser(
        private val message: String,
    ) : Parser<DeclareFunctionASTNode> {
        override fun Raise<ASTError>.parse(context: TokensContext): DeclareFunctionASTNode {
            raise(ASTError(message))
        }
    }

    private class FailingInitParser(
        private val message: String,
    ) : Parser<ImmutableVariableInitializationASTNode> {
        override fun Raise<ASTError>.parse(context: TokensContext): ImmutableVariableInitializationASTNode {
            raise(ASTError(message))
        }
    }

    private class StubBlockParser : Parser<BlockASTNode> {
        override fun Raise<ASTError>.parse(context: TokensContext): BlockASTNode {
            match<Token.Punctuation.LBrace>(context.consumeToken()) { buildError("{", it) }
            match<Token.Punctuation.RBrace>(context.consumeToken()) { buildError("}", it) }
            return BlockASTNode(emptyList())
        }
    }

    private fun defaultFunctionStub(): DeclareFunctionASTNode =
        DeclareFunctionASTNode("foo", FunctionArgsASTNode(emptyList()), BlockASTNode(emptyList()), Type.UnitType)

    private fun defaultInitStub(): ImmutableVariableInitializationASTNode =
        ImmutableVariableInitializationASTNode("x", Type.IntType, IntLiteralExpressionNode(0L))

    private fun realProgramParser(): ProgramParser {
        val expr = AbstractSyntaxTreeExpressionParser()
        val block = lazy { StubBlockParser() }
        return ProgramParser(FunctionParser(block), ImmutableInitializationParser(expr))
    }

    private fun parseProgram(
        tokens: List<Token>,
        functionParser: Parser<DeclareFunctionASTNode> = TaggingFunctionStubParser(defaultFunctionStub()),
        initParser: Parser<ImmutableVariableInitializationASTNode> = TaggingInitStubParser(defaultInitStub()),
    ): Either<ASTError, ProgramASTNode> {
        val parser = ProgramParser(functionParser, initParser)
        return either { with(parser) { parse(TokensContext(tokens)) } }
    }

    private fun parseFromSource(
        src: String,
        parser: ProgramParser = realProgramParser(),
    ): Either<ASTError, ProgramASTNode> {
        val tokens = Lexer(src).tokenize().getOrElse { error("lexer error: $it") }
        return either { with(parser) { parse(TokensContext(tokens)) } }
    }

    // region Positive scenarios

    @Test
    fun `parses empty program`() {
        val result = parseProgram(listOf(eof()))
            .getOrElse { error("unexpected parse error: $it") }

        assertTrue(result.functions.isEmpty())
        assertTrue(result.globalVariables.isEmpty())
    }

    @Test
    fun `routes fun to function parser stub`() {
        val functionStub = TaggingFunctionStubParser(defaultFunctionStub())
        val initStub = TaggingInitStubParser(defaultInitStub())

        val result = parseProgram(listOf(funKeyword(), eof()), functionStub, initStub)
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals(1, result.functions.size)
        assertTrue(result.globalVariables.isEmpty())
        assertTrue(functionStub.invoked)
        assertFalse(initStub.invoked)
    }

    @Test
    fun `routes val to init parser stub`() {
        val functionStub = TaggingFunctionStubParser(defaultFunctionStub())
        val initStub = TaggingInitStubParser(defaultInitStub())

        val result = parseProgram(listOf(valKeyword(), eof()), functionStub, initStub)
            .getOrElse { error("unexpected parse error: $it") }

        assertTrue(result.functions.isEmpty())
        assertEquals(1, result.globalVariables.size)
        assertFalse(functionStub.invoked)
        assertTrue(initStub.invoked)
    }

    @Test
    fun `returns result from invoked stub`() {
        val function = defaultFunctionStub()
        val functionStub = TaggingFunctionStubParser(function)

        val result = parseProgram(listOf(funKeyword(), eof()), functionStub, TaggingInitStubParser(defaultInitStub()))
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals(function, result.functions.single())
    }

    @Test
    fun `parses single function via integration`() {
        val result = parseFromSource("fun foo(): Unit { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals(1, result.functions.size)
        assertEquals("foo", result.functions.single().name)
        assertTrue(result.globalVariables.isEmpty())
    }

    @Test
    fun `parses function with implicit Unit return type via integration`() {
        val result = parseFromSource("fun main() { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals(1, result.functions.size)
        assertEquals("main", result.functions.single().name)
        assertEquals(Type.UnitType, result.functions.single().resultType)
    }

    @Test
    fun `parses single global variable via integration`() {
        val result = parseFromSource("val Pi: Double = 3.1417")
            .getOrElse { error("unexpected parse error: $it") }

        assertTrue(result.functions.isEmpty())
        assertEquals(1, result.globalVariables.size)
        val global = result.globalVariables.single()
        assertInstanceOf(ImmutableVariableInitializationASTNode::class.java, global)
        assertEquals("Pi", global.name)
        assertEquals(Type.DoubleType, global.type)
    }

    @Test
    fun `parses function then global like simple bred header`() {
        val result = parseFromSource(
            """
            fun min(a: Int, b: String): Unit { }
            val Pi: Double = 3.1417
            """.trimIndent(),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(1, result.functions.size)
        assertEquals(1, result.globalVariables.size)
        assertEquals("min", result.functions.single().name)
        assertEquals("Pi", result.globalVariables.single().name)
    }

    @Test
    fun `parses global then function preserving order`() {
        val result = parseFromSource(
            """
            val a: Int = 1
            fun f(): Unit { }
            """.trimIndent(),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals("a", result.globalVariables.single().name)
        assertEquals("f", result.functions.single().name)
    }

    @Test
    fun `parses two functions`() {
        val result = parseFromSource(
            """
            fun a(): Unit { }
            fun b(): Unit { }
            """.trimIndent(),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(2, result.functions.size)
        assertEquals(listOf("a", "b"), result.functions.map { it.name })
    }

    @Test
    fun `parses two global variables`() {
        val result = parseFromSource(
            """
            val a: Int = 1
            val b: Int = 2
            """.trimIndent(),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(2, result.globalVariables.size)
        assertEquals(listOf("a", "b"), result.globalVariables.map { it.name })
    }

    @Test
    fun `parses program with extra spaces`() {
        val result = parseFromSource("fun  foo  (  )  :  Unit  {  }")
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals("foo", result.functions.single().name)
    }

    @Test
    fun `parses function with empty body and global variable`() {
        val result = parseFromSource(
            """
            fun main(): Unit { }
            val x: Int = 0
            """.trimIndent(),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(1, result.functions.size)
        assertEquals(1, result.globalVariables.size)
        assertEquals(1, result.functions.single().block.statements.size)
        val returnStmt = assertInstanceOf(
            ReturnFunctionStatementASTNode::class.java,
            result.functions.single().block.statements.single(),
        )
        assertTrue(returnStmt.expression.isLeft())
        assertEquals(Type.UnitType, returnStmt.expression.leftOrNull())
    }

    // endregion

    // region Negative scenarios

    @Test
    fun `if at top level fails`() {
        val result = parseFromSource("if (true) { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected function or constant declaration") == true)
    }

    @Test
    fun `while at top level fails`() {
        val result = parseFromSource("while (true) { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected function or constant declaration") == true)
    }

    @Test
    fun `for at top level fails`() {
        val result = parseFromSource("for (i in 0 to 10) { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected function or constant declaration") == true)
    }

    @Test
    fun `assignment at top level fails`() {
        val result = parseFromSource("x = 1")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected function or constant declaration") == true)
    }

    @Test
    fun `lbrace at top level fails`() {
        val result = parseProgram(listOf(Token.Punctuation.LBrace(pos), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected function or constant declaration") == true)
    }

    @Test
    fun `plus at top level fails`() {
        val result = parseProgram(listOf(Token.Operator.Plus(pos), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected function or constant declaration") == true)
    }

    @Test
    fun `literal at top level fails`() {
        val result = parseProgram(listOf(Token.Literal.IntLiteral(1L, "1", pos), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected function or constant declaration") == true)
    }

    @Test
    fun `propagates function parser failure`() {
        val functionStub = TaggingFunctionStubParser(defaultFunctionStub())
        val initStub = TaggingInitStubParser(defaultInitStub())
        val result = parseProgram(listOf(funKeyword(), eof()), FailingFunctionParser("function stub failure"), initStub)
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("function stub failure") == true)
        assertFalse(initStub.invoked)
    }

    @Test
    fun `propagates init parser failure`() {
        val functionStub = TaggingFunctionStubParser(defaultFunctionStub())
        val initStub = TaggingInitStubParser(defaultInitStub())
        val result = parseProgram(listOf(valKeyword(), eof()), functionStub, FailingInitParser("init stub failure"))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("init stub failure") == true)
        assertFalse(functionStub.invoked)
    }

    @Test
    fun `malformed function fails`() {
        assertTrue(parseFromSource("fun foo(").isLeft())
    }

    @Test
    fun `malformed global variable fails`() {
        assertTrue(parseFromSource("val x: Int =").isLeft())
    }

    @Test
    fun `function with unknown argument type fails`() {
        val result = parseFromSource("fun foo(a: Foo): Unit { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Invalid type Foo") == true)
    }

    @Test
    fun `global with unknown type fails`() {
        val result = parseFromSource("val x: Foo = 1")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Invalid type Foo") == true)
    }

    @Test
    fun `only fun keyword fails`() {
        assertTrue(parseFromSource("fun").isLeft())
    }

    @Test
    fun `only val keyword fails`() {
        assertTrue(parseFromSource("val").isLeft())
    }

    @Test
    fun `else at top level fails`() {
        val result = parseFromSource("else { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected function or constant declaration") == true)
    }

    @Test
    fun `lparen at top level fails`() {
        val result = parseProgram(listOf(Token.Punctuation.LParen(pos), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Expected function or constant declaration") == true)
    }

    @Test
    fun `routing error does not invoke stubs`() {
        val functionStub = TaggingFunctionStubParser(defaultFunctionStub())
        val initStub = TaggingInitStubParser(defaultInitStub())
        parseProgram(listOf(Token.Operator.Plus(pos), eof()), functionStub, initStub)
        assertFalse(functionStub.invoked)
        assertFalse(initStub.invoked)
    }

    // endregion
}
