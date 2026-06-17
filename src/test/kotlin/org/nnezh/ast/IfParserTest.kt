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
import org.nnezh.ast.BinaryOperator
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.BooleanLiteralExpressionNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.FunctionCallExpressionNode
import org.nnezh.ast.IfStatementASTNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.StatementASTNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.Position
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.AstErrorFactory.buildError
import org.nnezh.org.nnezh.ast.parsers.IfParser
import org.nnezh.org.nnezh.ast.parsers.Parser

class IfParserTest {

    private val pos = Position(1, 1)

    private fun ifKeyword() = Token.Keyword.If(pos)

    private fun elseKeyword() = Token.Keyword.Else(pos)

    private fun lparen() = Token.Punctuation.LParen(pos)

    private fun rparen() = Token.Punctuation.RParen(pos)

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

    private class SequentialStubBlockParser(
        private val blocks: List<BlockASTNode>,
        private val onParse: ((Int, TokensContext) -> Unit)? = null,
    ) : Parser<BlockASTNode> {
        private var callIndex = 0

        override fun Raise<ASTError>.parse(context: TokensContext): BlockASTNode {
            onParse?.invoke(callIndex, context)
            match<Token.Punctuation.LBrace>(context.consumeToken()) { buildError("{", it) }
            match<Token.Punctuation.RBrace>(context.consumeToken()) { buildError("}", it) }
            return blocks[callIndex++]
        }
    }

    private fun parseIf(
        tokens: List<Token>,
        expressionParser: Parser<ExpressionASTNode> = StubExpressionParser(IntLiteralExpressionNode(0L)),
        blockParser: Parser<BlockASTNode> = StubBlockParser(),
    ): Either<ASTError, IfStatementASTNode> {
        val parser = IfParser(expressionParser, lazy { blockParser })
        return either { with(parser) { parse(TokensContext(tokens)) } }
    }

    private fun parseFromSource(
        src: String,
        blockParser: Parser<BlockASTNode> = StubBlockParser(),
    ): Either<ASTError, IfStatementASTNode> {
        val tokens = Lexer(src).tokenize().getOrElse { error("lexer error: $it") }
        return parseIf(tokens, AbstractSyntaxTreeExpressionParser(), blockParser)
    }

    private fun ifTokensWithoutElse(vararg tail: Token): List<Token> =
        listOf(ifKeyword(), lparen(), rparen(), *tail)

    // region Positive scenarios

    @Test
    fun `parses if without else and delegates to nested parsers`() {
        val condition = IntLiteralExpressionNode(1L)
        val thenBlock = BlockASTNode(emptyList())
        val result = parseIf(
            ifTokensWithoutElse(lbrace(), rbrace(), eof()),
            StubExpressionParser(condition),
            StubBlockParser(thenBlock),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(condition, result.condition)
        assertEquals(thenBlock, result.thenBlock)
        assertTrue(result.elseBlock.isRight())
    }

    @Test
    fun `parses if with else branch`() {
        val condition = IntLiteralExpressionNode(1L)
        val thenBlock = BlockASTNode(emptyList())
        val elseBlock = BlockASTNode(emptyList())
        val result = parseIf(
            listOf(ifKeyword(), lparen(), rparen(), lbrace(), rbrace(), elseKeyword(), lbrace(), rbrace(), eof()),
            StubExpressionParser(condition),
            SequentialStubBlockParser(listOf(thenBlock, elseBlock)),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(condition, result.condition)
        assertEquals(thenBlock, result.thenBlock)
        assertTrue(result.elseBlock.isLeft())
        assertEquals(elseBlock, result.elseBlock.leftOrNull())
    }

    @Test
    fun `passes first token inside parens to expression parser`() {
        val conditionToken = identifier("x")
        var tokenAtExpressionStart: Token? = null

        val consumingStub = object : Parser<ExpressionASTNode> {
            override fun Raise<ASTError>.parse(context: TokensContext): ExpressionASTNode {
                tokenAtExpressionStart = context.top()
                context.consumeToken()
                return VariableExpressionNode(conditionToken)
            }
        }

        parseIf(
            listOf(ifKeyword(), lparen(), conditionToken, rparen(), lbrace(), rbrace(), eof()),
            consumingStub,
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(conditionToken, tokenAtExpressionStart)
    }

    @Test
    fun `passes context after closing paren to then block parser`() {
        var tokenAtThenBlockStart: Token? = null

        parseFromSource(
            "if (x > 0) { }",
            StubBlockParser(onParse = { tokenAtThenBlockStart = it.top() }),
        ).getOrElse { error("unexpected parse error: $it") }

        assertInstanceOf(Token.Punctuation.LBrace::class.java, tokenAtThenBlockStart)
    }

    @Test
    fun `parses if with comparison condition via lexer`() {
        val result = parseFromSource("if (x > 0) { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertInstanceOf(BinaryExpressionASTNode::class.java, result.condition)
        val binary = result.condition as BinaryExpressionASTNode
        assertInstanceOf(VariableExpressionNode::class.java, binary.left)
        assertEquals(BinaryOperator.Gt, binary.operator.kind)
        assertInstanceOf(IntLiteralExpressionNode::class.java, binary.right)
        assertTrue(result.elseBlock.isRight())
    }

    @Test
    fun `parses if with else via lexer`() {
        val result = parseFromSource("if (d > c) { } else { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertInstanceOf(BinaryExpressionASTNode::class.java, result.condition)
        assertTrue(result.thenBlock.statements.isEmpty())
        assertTrue(result.elseBlock.isLeft())
        assertTrue(result.elseBlock.leftOrNull()!!.statements.isEmpty())
    }

    @Test
    fun `parses if with complex condition expression`() {
        val result = parseFromSource("if (a + b * 2 > 0) { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertInstanceOf(BinaryExpressionASTNode::class.java, result.condition)
    }

    @Test
    fun `parses if with boolean literal condition`() {
        val result = parseFromSource("if (true) { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertInstanceOf(BooleanLiteralExpressionNode::class.java, result.condition)
        assertEquals(true, (result.condition as BooleanLiteralExpressionNode).value)
    }

    @Test
    fun `parses if with empty then and else blocks`() {
        val result = parseFromSource("if (x > 0) { } else { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertTrue(result.thenBlock.statements.isEmpty())
        assertTrue(result.elseBlock.isLeft())
        assertTrue(result.elseBlock.leftOrNull()!!.statements.isEmpty())
    }

    @Test
    fun `uses different blocks for then and else from block parser`() {
        val thenStatement: StatementASTNode =
            AssignmentStatementASTNode("a", IntLiteralExpressionNode(1L))
        val elseStatement: StatementASTNode =
            AssignmentStatementASTNode("b", IntLiteralExpressionNode(2L))
        val thenBlock = BlockASTNode(listOf(thenStatement))
        val elseBlock = BlockASTNode(listOf(elseStatement))

        val result = parseIf(
            listOf(ifKeyword(), lparen(), rparen(), lbrace(), rbrace(), elseKeyword(), lbrace(), rbrace(), eof()),
            StubExpressionParser(IntLiteralExpressionNode(0L)),
            SequentialStubBlockParser(listOf(thenBlock, elseBlock)),
        ).getOrElse { error("unexpected parse error: $it") }

        assertEquals(thenBlock, result.thenBlock)
        assertTrue(result.elseBlock.isLeft())
        assertEquals(elseBlock, result.elseBlock.leftOrNull())
    }

    @Test
    fun `parses if with extra spaces via lexer`() {
        val result = parseFromSource("if  ( x > 0 )  {  }  else  {  }")
            .getOrElse { error("unexpected parse error: $it") }

        assertInstanceOf(BinaryExpressionASTNode::class.java, result.condition)
        assertTrue(result.elseBlock.isLeft())
    }

    @Test
    fun `parses if with function call in condition`() {
        val result = parseFromSource("if (loop() > 1) { }")
            .getOrElse { error("unexpected parse error: $it") }

        assertInstanceOf(BinaryExpressionASTNode::class.java, result.condition)
        val binary = result.condition as BinaryExpressionASTNode
        assertInstanceOf(FunctionCallExpressionNode::class.java, binary.left)
        assertEquals("loop", (binary.left as FunctionCallExpressionNode).name.lexeme)
    }

    // endregion

    // region Negative scenarios

    @Test
    fun `empty input fails`() {
        assertTrue(parseIf(listOf(eof())).isLeft())
    }

    @Test
    fun `missing if keyword fails`() {
        assertTrue(parseIf(listOf(lparen(), rparen(), lbrace(), rbrace(), eof())).isLeft())
    }

    @Test
    fun `while instead of if fails`() {
        val result = parseIf(listOf(Token.Keyword.While(pos), lparen(), rparen(), lbrace(), rbrace(), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("if") == true)
    }

    @Test
    fun `for instead of if fails`() {
        val result = parseIf(listOf(Token.Keyword.For(pos), lparen(), rparen(), lbrace(), rbrace(), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("if") == true)
    }

    @Test
    fun `val instead of if fails`() {
        val result = parseIf(listOf(Token.Keyword.Val(pos), lparen(), rparen(), lbrace(), rbrace(), eof()))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("if") == true)
    }

    @Test
    fun `only if keyword fails`() {
        assertTrue(parseIf(listOf(ifKeyword(), eof())).isLeft())
    }

    @Test
    fun `if without lparen fails`() {
        val result = parseFromSource("if x > 0) { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("(") == true)
    }

    @Test
    fun `if with lparen but no condition fails`() {
        assertTrue(parseFromSource("if (").isLeft())
    }

    @Test
    fun `if without rparen fails`() {
        val result = parseFromSource("if (x > 0")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains(")") == true)
    }

    @Test
    fun `if without then block fails`() {
        val result = parseFromSource("if (x > 0)")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("{") == true)
    }

    @Test
    fun `malformed condition inside parens fails`() {
        assertTrue(parseFromSource("if (x >) { }").isLeft())
    }

    @Test
    fun `unclosed then block fails`() {
        assertTrue(parseFromSource("if (x > 0) {").isLeft())
    }

    @Test
    fun `rbrace instead of lbrace after condition fails`() {
        val result = parseFromSource("if (x > 0) }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("{") == true)
    }

    @Test
    fun `literal instead of if fails`() {
        val result = parseIf(
            listOf(Token.Literal.IntLiteral(1L, "1", pos), lparen(), rparen(), lbrace(), rbrace(), eof()),
        )
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("if") == true)
    }

    @Test
    fun `empty parenthesized condition fails`() {
        assertTrue(parseFromSource("if () { }").isLeft())
    }

    @Test
    fun `else without block fails`() {
        assertTrue(parseFromSource("if (x > 0) { } else").isLeft())
    }

    @Test
    fun `else without then block fails`() {
        val result = parseFromSource("if (x > 0) else { }")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.message?.contains("{") == true)
    }

    @Test
    fun `extra rparen fails`() {
        assertTrue(parseFromSource("if (x > 0)) { }").isLeft())
    }

    // endregion
}
