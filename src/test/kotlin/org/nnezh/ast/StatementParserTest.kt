/*
 * This file was generated with the assistance of AI (Cursor).
 */

package org.nnezh.org.nnezh.ast

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.right
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.ast.AssignmentStatementASTNode
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.CallFunctionStatementASTNode
import org.nnezh.ast.EmptyNode
import org.nnezh.ast.ForStatementASTNode
import org.nnezh.ast.IfStatementASTNode
import org.nnezh.ast.ImmutableVariableInitializationASTNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.StatementASTNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.ast.WhileStatementASTNode
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.Position
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.Type
import org.nnezh.org.nnezh.ast.AstErrorFactory.buildError

class StatementParserTest {

    private val pos = Position(1, 1)

    private fun identifier(name: String) = Token.Identifier(name, pos)

    private fun assign() = Token.Operator.Assign(pos)

    private fun lparen() = Token.Punctuation.LParen(pos)

    private fun eof() = Token.Eof(pos)

    private class TaggingStubParser(
        val result: StatementASTNode,
    ) : Parser<StatementASTNode> {
        var invoked: Boolean = false

        override fun Raise<ASTError>.parse(context: TokensContext): StatementASTNode {
            invoked = true
            return result
        }
    }

    private class FailingStatementParser(
        private val message: String,
    ) : Parser<StatementASTNode> {
        override fun Raise<ASTError>.parse(context: TokensContext): StatementASTNode {
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

    private data class StatementParserStubs(
        val ifStub: TaggingStubParser,
        val whileStub: TaggingStubParser,
        val forStub: TaggingStubParser,
        val initStub: TaggingStubParser,
        val assignStub: TaggingStubParser,
        val callStub: TaggingStubParser,
    ) {
        fun all(): List<TaggingStubParser> = listOf(ifStub, whileStub, forStub, initStub, assignStub, callStub)

        fun parser(): StatementParser = StatementParser(
            ifStub,
            whileStub,
            forStub,
            initStub,
            assignStub,
            callStub,
        )
    }

    private fun defaultStubs(): StatementParserStubs {
        val emptyBlock = BlockASTNode(emptyList())
        val id = identifier("stub")
        return StatementParserStubs(
            ifStub = TaggingStubParser(
                IfStatementASTNode(IntLiteralExpressionNode(0L), emptyBlock, EmptyNode().right()),
            ),
            whileStub = TaggingStubParser(
                WhileStatementASTNode(IntLiteralExpressionNode(0L), emptyBlock),
            ),
            forStub = TaggingStubParser(
                ForStatementASTNode(emptyBlock),
            ),
            initStub = TaggingStubParser(
                ImmutableVariableInitializationASTNode("i", Type.IntType, IntLiteralExpressionNode(0L)),
            ),
            assignStub = TaggingStubParser(
                AssignmentStatementASTNode("x", IntLiteralExpressionNode(0L)),
            ),
            callStub = TaggingStubParser(
                CallFunctionStatementASTNode(VariableExpressionNode(id)),
            ),
        )
    }

    private fun realStatementParser(): StatementParser {
        val expr = AbstractSyntaxTreeExpressionParser()
        val block = lazy { StubBlockParser() }
        return StatementParser(
            ifParser = IfParser(expr, block),
            whileParser = WhileParser(expr, block),
            forParser = ForParser(expr, block),
            immutableInitializationParser = ImmutableInitializationParser(expr),
            assignParser = AssignParser(expr),
            callParser = CallStatementParser(expr),
        )
    }

    private fun parseStatement(
        tokens: List<Token>,
        parser: StatementParser = defaultStubs().parser(),
    ): Either<ASTError, StatementASTNode> =
        either { with(parser) { parse(TokensContext(tokens)) } }

    private fun parseFromSource(
        src: String,
        parser: StatementParser = realStatementParser(),
    ): Either<ASTError, StatementASTNode> {
        val tokens = Lexer(src).tokenize().getOrElse { error("lexer error: $it") }
        return parseStatement(tokens, parser)
    }

    private fun assertOnlyStubInvoked(stubs: StatementParserStubs, expected: TaggingStubParser) {
        for (stub in stubs.all()) {
            if (stub === expected) {
                assertTrue(stub.invoked, "expected stub to be invoked")
            } else {
                assertFalse(stub.invoked, "unexpected stub invocation")
            }
        }
    }

    // region Positive routing scenarios

    @Test
    fun `routes val to init parser`() {
        val stubs = defaultStubs()
        parseStatement(listOf(Token.Keyword.Val(pos), eof()), stubs.parser())
            .getOrElse { error("unexpected parse error: $it") }
        assertOnlyStubInvoked(stubs, stubs.initStub)
    }

    @Test
    fun `routes identifier without lparen to assign parser`() {
        val stubs = defaultStubs()
        parseStatement(listOf(identifier("x"), assign(), eof()), stubs.parser())
            .getOrElse { error("unexpected parse error: $it") }
        assertOnlyStubInvoked(stubs, stubs.assignStub)
    }

    @Test
    fun `routes identifier with lparen to call parser`() {
        val stubs = defaultStubs()
        parseStatement(listOf(identifier("foo"), lparen(), eof()), stubs.parser())
            .getOrElse { error("unexpected parse error: $it") }
        assertOnlyStubInvoked(stubs, stubs.callStub)
    }

    @Test
    fun `routes if to if parser`() {
        val stubs = defaultStubs()
        parseStatement(listOf(Token.Keyword.If(pos), eof()), stubs.parser())
            .getOrElse { error("unexpected parse error: $it") }
        assertOnlyStubInvoked(stubs, stubs.ifStub)
    }

    @Test
    fun `routes while to while parser`() {
        val stubs = defaultStubs()
        parseStatement(listOf(Token.Keyword.While(pos), eof()), stubs.parser())
            .getOrElse { error("unexpected parse error: $it") }
        assertOnlyStubInvoked(stubs, stubs.whileStub)
    }

    @Test
    fun `routes for to for parser`() {
        val stubs = defaultStubs()
        parseStatement(listOf(Token.Keyword.For(pos), eof()), stubs.parser())
            .getOrElse { error("unexpected parse error: $it") }
        assertOnlyStubInvoked(stubs, stubs.forStub)
    }

    @Test
    fun `returns result from invoked stub`() {
        val stubs = defaultStubs()
        val result = parseStatement(listOf(Token.Keyword.Val(pos), eof()), stubs.parser())
            .getOrElse { error("unexpected parse error: $it") }
        assertEquals(stubs.initStub.result, result)
    }

    @Test
    fun `invokes exactly one stub per parse call`() {
        val stubs = defaultStubs()
        parseStatement(listOf(identifier("x"), lparen(), eof()), stubs.parser())
            .getOrElse { error("unexpected parse error: $it") }
        assertEquals(1, stubs.all().count { it.invoked })
    }

    // endregion

    // region Positive integration scenarios

    @Test
    fun `parses val initialization via real parsers`() {
        val result = parseFromSource("val n: Int = 42")
            .getOrElse { error("unexpected parse error: $it") }
        assertInstanceOf(ImmutableVariableInitializationASTNode::class.java, result)
        assertEquals("n", (result as ImmutableVariableInitializationASTNode).name)
    }

    @Test
    fun `parses assignment via real parsers`() {
        val result = parseFromSource("x = 1")
            .getOrElse { error("unexpected parse error: $it") }
        assertInstanceOf(AssignmentStatementASTNode::class.java, result)
        assertEquals("x", (result as AssignmentStatementASTNode).name)
    }

    @Test
    fun `parses function call statement via real parsers`() {
        val result = parseFromSource("println(a)")
            .getOrElse { error("unexpected parse error: $it") }
        assertInstanceOf(CallFunctionStatementASTNode::class.java, result)
    }

    @Test
    fun `parses if statement via real parsers`() {
        val result = parseFromSource("if (true) { }")
            .getOrElse { error("unexpected parse error: $it") }
        assertInstanceOf(IfStatementASTNode::class.java, result)
    }

    @Test
    fun `parses while statement via real parsers`() {
        val result = parseFromSource("while true { }")
            .getOrElse { error("unexpected parse error: $it") }
        assertInstanceOf(WhileStatementASTNode::class.java, result)
    }

    @Test
    fun `parses for statement via real parsers`() {
        val result = parseFromSource("for (i in 0 to 10) { }")
            .getOrElse { error("unexpected parse error: $it") }
        assertInstanceOf(ForStatementASTNode::class.java, result)
    }

    // endregion

    // region Negative scenarios

    @Test
    fun `empty input fails`() {
        assertTrue(parseStatement(listOf(eof())).isLeft())
    }

    @Test
    fun `eof only fails`() {
        assertTrue(parseFromSource("", defaultStubs().parser()).isLeft())
    }

    @Test
    fun `int literal fails with didnt expect`() {
        val result = parseStatement(listOf(Token.Literal.IntLiteral(1L, "1", pos), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Didn't expect") == true)
    }

    @Test
    fun `plus operator fails with didnt expect`() {
        val result = parseStatement(listOf(Token.Operator.Plus(pos), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Didn't expect") == true)
    }

    @Test
    fun `lbrace fails with didnt expect`() {
        val result = parseStatement(listOf(Token.Punctuation.LBrace(pos), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Didn't expect") == true)
    }

    @Test
    fun `fun keyword fails with didnt expect`() {
        val result = parseStatement(listOf(Token.Keyword.Fun(pos), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Didn't expect") == true)
    }

    @Test
    fun `in keyword fails with didnt expect`() {
        val result = parseStatement(listOf(Token.Keyword.In(pos), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Didn't expect") == true)
    }

    @Test
    fun `else keyword fails with didnt expect`() {
        val result = parseStatement(listOf(Token.Keyword.Else(pos), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Didn't expect") == true)
    }

    @Test
    fun `lparen fails with didnt expect`() {
        val result = parseStatement(listOf(lparen(), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Didn't expect") == true)
    }

    @Test
    fun `assign operator fails with didnt expect`() {
        val result = parseStatement(listOf(assign(), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Didn't expect") == true)
    }

    @Test
    fun `propagates init parser failure`() {
        val stubs = defaultStubs()
        val parser = StatementParser(
            stubs.ifStub,
            stubs.whileStub,
            stubs.forStub,
            FailingStatementParser("init stub failure"),
            stubs.assignStub,
            stubs.callStub,
        )
        val result = parseStatement(listOf(Token.Keyword.Val(pos), eof()), parser)
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("init stub failure") == true)
    }

    @Test
    fun `malformed assignment fails in assign parser`() {
        assertTrue(parseFromSource("x =").isLeft())
    }

    @Test
    fun `identifier with eof fails in assign parser`() {
        assertTrue(parseFromSource("x").isLeft())
    }

    @Test
    fun `unknown token does not invoke any stub`() {
        val stubs = defaultStubs()
        parseStatement(listOf(Token.Operator.Plus(pos), eof()), stubs.parser())
        assertTrue(stubs.all().none { it.invoked })
    }

    /**
     * Documents current behavior: routing uses [TokensContext.top] with offset 1
     * without bounds check. Malformed token stream without trailing Eof throws
     * instead of returning [Either.Left].
     */
    @Test
    fun `identifier without eof throws instead of returning parse error`() {
        val stubs = defaultStubs()
        assertThrows(IndexOutOfBoundsException::class.java) {
            parseStatement(listOf(identifier("x")), stubs.parser())
        }
        assertTrue(stubs.all().none { it.invoked })
    }

    // endregion
}
