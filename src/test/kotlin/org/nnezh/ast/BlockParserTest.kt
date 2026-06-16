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
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.ImmutableVariableInitializationASTNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.MutableVariableInitializationASTNode
import org.nnezh.ast.StatementASTNode
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.Position
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.Type

class BlockParserTest {

    private val pos = Position(1, 1)

    private fun lbrace() = Token.Punctuation.LBrace(pos)

    private fun rbrace() = Token.Punctuation.RBrace(pos)

    private fun identifier(name: String) = Token.Identifier(name, pos)

    private fun eof() = Token.Eof(pos)

    private data class StubStatementSpec(
        val result: StatementASTNode,
        val tokensToConsume: Int,
    )

    private class StubStatementParser(
        private val result: StatementASTNode,
        private val tokensToConsume: Int = 1,
        private val onParse: ((TokensContext) -> Unit)? = null,
    ) : Parser<StatementASTNode> {
        override fun Raise<ASTError>.parse(context: TokensContext): StatementASTNode {
            onParse?.invoke(context)
            repeat(tokensToConsume) { context.consumeToken() }
            return result
        }
    }

    private class SequentialStubStatementParser(
        private val specs: List<StubStatementSpec>,
        private val onParse: ((Int, TokensContext) -> Unit)? = null,
    ) : Parser<StatementASTNode> {
        private var callIndex = 0

        override fun Raise<ASTError>.parse(context: TokensContext): StatementASTNode {
            val spec = specs[callIndex]
            onParse?.invoke(callIndex, context)
            repeat(spec.tokensToConsume) { context.consumeToken() }
            callIndex++
            return spec.result
        }
    }

    private class FailingStubStatementParser(
        private val succeedCount: Int,
        private val result: StatementASTNode,
        private val tokensToConsume: Int = 1,
        private val failureMessage: String = "stub statement failure",
    ) : Parser<StatementASTNode> {
        private var callIndex = 0

        override fun Raise<ASTError>.parse(context: TokensContext): StatementASTNode {
            if (callIndex >= succeedCount) {
                raise(ASTError(failureMessage))
            }
            repeat(tokensToConsume) { context.consumeToken() }
            callIndex++
            return result
        }
    }

    private fun assignStatementParser(): Parser<StatementASTNode> =
        AssignParser(AbstractSyntaxTreeExpressionParser())

    private fun initStatementParser(): Parser<StatementASTNode> =
        ImmutableInitializationParser(AbstractSyntaxTreeExpressionParser())

    private fun varInitStatementParser(): Parser<StatementASTNode> =
        MutableInitializationParser(AbstractSyntaxTreeExpressionParser())

    private fun parseBlock(
        tokens: List<Token>,
        statementParser: Parser<StatementASTNode> = StubStatementParser(
            AssignmentStatementASTNode("x", IntLiteralExpressionNode(0L)),
        ),
    ): Either<ASTError, BlockASTNode> {
        val parser = BlockParser(lazy { statementParser })
        return either { with(parser) { parse(TokensContext(tokens)) } }
    }

    private fun parseFromSource(
        src: String,
        statementParser: Parser<StatementASTNode>,
    ): Either<ASTError, BlockASTNode> {
        val tokens = Lexer(src).tokenize().getOrElse { error("lexer error: $it") }
        return parseBlock(tokens, statementParser)
    }

    // region Positive scenarios

    @Test
    fun `parses empty block`() {
        val result = parseBlock(listOf(lbrace(), rbrace(), eof()))
            .getOrElse { error("unexpected parse error: $it") }

        assertTrue(result.statements.isEmpty())
    }

    @Test
    fun `parses block with single stub statement`() {
        val statement = AssignmentStatementASTNode("x", IntLiteralExpressionNode(1L))
        val result = parseBlock(
            listOf(lbrace(), identifier("x"), rbrace(), eof()),
            StubStatementParser(statement, tokensToConsume = 1),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(1, result.statements.size)
        assertEquals(statement, result.statements.single())
    }

    @Test
    fun `parses block with multiple sequential stub statements`() {
        val s1 = AssignmentStatementASTNode("a", IntLiteralExpressionNode(1L))
        val s2 = AssignmentStatementASTNode("b", IntLiteralExpressionNode(2L))
        val s3 = AssignmentStatementASTNode("c", IntLiteralExpressionNode(3L))
        val result = parseBlock(
            listOf(lbrace(), identifier("a"), identifier("b"), identifier("c"), rbrace(), eof()),
            SequentialStubStatementParser(
                listOf(
                    StubStatementSpec(s1, 1),
                    StubStatementSpec(s2, 1),
                    StubStatementSpec(s3, 1),
                ),
            ),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(listOf(s1, s2, s3), result.statements)
    }

    @Test
    fun `passes correct token to statement parser on each call`() {
        val tokensAtCall = mutableListOf<Token>()
        val tokenA = identifier("a")
        val tokenB = identifier("b")

        parseBlock(
            listOf(lbrace(), tokenA, tokenB, rbrace(), eof()),
            SequentialStubStatementParser(
                specs = listOf(
                    StubStatementSpec(AssignmentStatementASTNode("a", IntLiteralExpressionNode(1L)), 1),
                    StubStatementSpec(AssignmentStatementASTNode("b", IntLiteralExpressionNode(2L)), 1),
                ),
                onParse = { index, context -> tokensAtCall.add(context.top()) },
            ),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(listOf(tokenA, tokenB), tokensAtCall)
    }

    @Test
    fun `parses block with assignment via lexer`() {
        val result = parseFromSource("{ x = 1 }", assignStatementParser())
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals(1, result.statements.size)
        val assignment = result.statements.single()
        assertInstanceOf(AssignmentStatementASTNode::class.java, assignment)
        assertEquals("x", (assignment as AssignmentStatementASTNode).name)
        assertEquals(1L, (assignment.value as IntLiteralExpressionNode).value)
    }

    @Test
    fun `parses block with two assignments via lexer`() {
        val result = parseFromSource("{ x = 1 y = 2 }", assignStatementParser())
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals(2, result.statements.size)
        assertEquals("x", (result.statements[0] as AssignmentStatementASTNode).name)
        assertEquals("y", (result.statements[1] as AssignmentStatementASTNode).name)
    }

    @Test
    fun `parses block with val initialization via lexer`() {
        val result = parseFromSource("{ val n: Int = 42 }", initStatementParser())
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals(1, result.statements.size)
        val init = result.statements.single()
        assertInstanceOf(ImmutableVariableInitializationASTNode::class.java, init)
        assertEquals("n", (init as ImmutableVariableInitializationASTNode).name)
        assertEquals(Type.IntType, init.type)
        assertEquals(42L, (init.value as IntLiteralExpressionNode).value)
    }

    @Test
    fun `parses block with var initialization via lexer`() {
        val result = parseFromSource("{ var n: Int = 42 }", varInitStatementParser())
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals(1, result.statements.size)
        val init = result.statements.single()
        assertInstanceOf(MutableVariableInitializationASTNode::class.java, init)
        assertEquals("n", (init as MutableVariableInitializationASTNode).name)
        assertEquals(Type.IntType, init.type)
        assertEquals(42L, (init.value as IntLiteralExpressionNode).value)
    }

    @Test
    fun `parses block with extra spaces via lexer`() {
        val result = parseFromSource("{  x = 1  }", assignStatementParser())
            .getOrElse { error("unexpected parse error: $it") }

        assertEquals(1, result.statements.size)
        assertEquals("x", (result.statements.single() as AssignmentStatementASTNode).name)
    }

    @Test
    fun `preserves different statement types from sequential stub`() {
        val assignment = AssignmentStatementASTNode("x", IntLiteralExpressionNode(1L))
        val init = ImmutableVariableInitializationASTNode("n", Type.IntType, IntLiteralExpressionNode(0L))

        val result = parseBlock(
            listOf(lbrace(), identifier("x"), identifier("n"), rbrace(), eof()),
            SequentialStubStatementParser(
                listOf(
                    StubStatementSpec(assignment, 1),
                    StubStatementSpec(init, 1),
                ),
            ),
        ).getOrElse { error("unexpected parse error: $it") }

        assertInstanceOf(AssignmentStatementASTNode::class.java, result.statements[0])
        assertInstanceOf(ImmutableVariableInitializationASTNode::class.java, result.statements[1])
    }

    @Test
    fun `does not invoke statement parser for empty block with trailing tokens`() {
        var callCount = 0

        val result = parseBlock(
            listOf(lbrace(), rbrace(), identifier("tail"), eof()),
            StubStatementParser(
                AssignmentStatementASTNode("x", IntLiteralExpressionNode(0L)),
                onParse = { callCount++ },
            ),
        ).getOrElse { error("unexpected parse error: $it") }

        assertTrue(result.statements.isEmpty())
        assertEquals(0, callCount)
    }

    @Test
    fun `parses empty block followed by eof`() {
        val result = parseBlock(listOf(lbrace(), rbrace(), eof()))
            .getOrElse { error("unexpected parse error: $it") }

        assertTrue(result.statements.isEmpty())
    }

    @Test
    fun `advances with different tokensToConsume per statement`() {
        val result = parseBlock(
            listOf(
                lbrace(),
                identifier("a"),
                identifier("b"),
                identifier("c"),
                identifier("d"),
                rbrace(),
                eof(),
            ),
            SequentialStubStatementParser(
                listOf(
                    StubStatementSpec(AssignmentStatementASTNode("a", IntLiteralExpressionNode(1L)), 2),
                    StubStatementSpec(AssignmentStatementASTNode("b", IntLiteralExpressionNode(2L)), 2),
                ),
            ),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(2, result.statements.size)
    }

    // endregion

    // region Negative scenarios

    @Test
    fun `empty input fails`() {
        assertTrue(parseBlock(listOf(eof())).isLeft())
    }

    @Test
    fun `missing lbrace fails`() {
        val result = parseBlock(listOf(identifier("x"), rbrace(), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("begin of block") == true)
    }

    @Test
    fun `rbrace instead of lbrace fails`() {
        val result = parseBlock(listOf(rbrace(), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("begin of block") == true)
    }

    @Test
    fun `only lbrace fails`() {
        val result = parseBlock(listOf(lbrace(), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Unexpected EOF") == true)
    }

    @Test
    fun `lbrace followed by eof fails`() {
        assertTrue(parseFromSource("{", assignStatementParser()).isLeft())
    }

    @Test
    fun `unclosed block with assignment fails`() {
        val result = parseFromSource("{ x = 1", assignStatementParser())
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Unexpected EOF") == true)
    }

    @Test
    fun `unclosed block with stub statement fails`() {
        val result = parseBlock(
            listOf(lbrace(), identifier("x"), eof()),
            StubStatementParser(AssignmentStatementASTNode("x", IntLiteralExpressionNode(0L)), tokensToConsume = 1),
        )
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("Unexpected EOF") == true)
    }

    @Test
    fun `rparen instead of lbrace fails`() {
        val result = parseBlock(listOf(Token.Punctuation.RParen(pos), rbrace(), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("begin of block") == true)
    }

    @Test
    fun `literal instead of lbrace fails`() {
        val result = parseBlock(
            listOf(Token.Literal.IntLiteral(1L, "1", pos), rbrace(), eof()),
        )
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("begin of block") == true)
    }

    @Test
    fun `invalid statement inside block fails`() {
        assertTrue(parseFromSource("{ + }", assignStatementParser()).isLeft())
    }

    @Test
    fun `propagates statement parser failure on second statement`() {
        val result = parseBlock(
            listOf(lbrace(), identifier("a"), identifier("b"), rbrace(), eof()),
            FailingStubStatementParser(
                succeedCount = 1,
                result = AssignmentStatementASTNode("a", IntLiteralExpressionNode(1L)),
                failureMessage = "stub statement failure",
            ),
        )
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("stub statement failure") == true)
    }

    @Test
    fun `malformed val initialization inside block fails`() {
        assertTrue(parseFromSource("{ val x: Int = }", initStatementParser()).isLeft())
    }

    // endregion
}
