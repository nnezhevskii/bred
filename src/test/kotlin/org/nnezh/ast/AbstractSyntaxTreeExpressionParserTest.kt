/*
 * This file was generated with the assistance of AI (Cursor).
 */

package org.nnezh.org.nnezh.ast

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.ast.ArrayAccessExpressionASTNode
import org.nnezh.ast.BinaryExpressionASTNode
import org.nnezh.ast.BooleanLiteralExpressionNode
import org.nnezh.ast.DoubleLiteralExpressionNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.FunctionCallExpressionNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.StaticArrayInitializationExpressionsListNode
import org.nnezh.ast.StringLiteralExpressionNode
import org.nnezh.ast.UnaryExpressionASTNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.lexer.Lexer

class AbstractSyntaxTreeExpressionParserTest {

    private fun parse(src: String): Either<ASTError, ExpressionASTNode> {
        val tokens = Lexer(src).tokenize().getOrElse { error("lex error: $it") }
        val parser = AbstractSyntaxTreeExpressionParser()
        return either { with(parser) { parse(TokensContext(tokens)) } }
    }

    /** Renders an expression tree as an S-expression for readable assertions. */
    private fun render(node: ExpressionASTNode): String = when (node) {
        is IntLiteralExpressionNode -> node.value.toString()
        is DoubleLiteralExpressionNode -> node.value.toString()
        is BooleanLiteralExpressionNode -> node.value.toString()
        is StringLiteralExpressionNode -> "\"${node.value}\""
        is VariableExpressionNode -> node.token.lexeme
        is UnaryExpressionASTNode -> "(${node.operator.lexeme} ${render(node.operand)})"
        is BinaryExpressionASTNode -> "(${node.operator.lexeme} ${render(node.left)} ${render(node.right)})"
        is FunctionCallExpressionNode ->
            "${node.name.lexeme}(${node.arguments.joinToString(", ") { render(it) }})"

        is ArrayAccessExpressionASTNode -> "${node.array}[${render(node.index)}]"
        is StaticArrayInitializationExpressionsListNode ->
            "[${node.values.joinToString(", ") { render(it) }}]"
    }

    private fun rendered(src: String): String =
        render(parse(src).getOrElse { error("unexpected parse error: $it") })

    // region Positive scenarios

    @Test
    fun `single literal and variable`() {
        assertEquals("42", rendered("42"))
        assertEquals("x", rendered("x"))
    }

    @Test
    fun `addition is left associative`() {
        assertEquals("(+ (+ 3 5) x)", rendered("3 + 5 + x"))
    }

    @Test
    fun `multiplication binds tighter than addition`() {
        assertEquals("(+ a (* b c))", rendered("a + b * c"))
    }

    @Test
    fun `parentheses override precedence`() {
        assertEquals("(* (+ a b) c)", rendered("(a + b) * c"))
    }

    @Test
    fun `complex expression with calls and grouping`() {
        assertEquals(
            "(+ (* 2 x) (* (/ (* y 5) 2) max(3, 5)))",
            rendered("2 * x + (y * 5) / 2 * max(3, 5)"),
        )
    }

    @Test
    fun `comparison and logical precedence`() {
        assertEquals("(> a b)", rendered("a > b"))
        assertEquals("(&& (== a b) (!= c d))", rendered("a == b && c != d"))
    }

    @Test
    fun `logical or binds looser than and`() {
        assertEquals("(|| a (&& b c))", rendered("a || b && c"))
    }

    @Test
    fun `unary operators`() {
        assertEquals("(+ (- x) 1)", rendered("-x + 1"))
        assertEquals("(! flag)", rendered("!flag"))
        assertEquals("(- (- x))", rendered("--x"))
    }

    @Test
    fun `function calls with varying arity`() {
        assertEquals("f()", rendered("f()"))
        assertEquals("g(1)", rendered("g(1)"))
        assertEquals("h(1, 2, 3)", rendered("h(1, 2, 3)"))
    }

    @Test
    fun `nested function calls and expression arguments`() {
        assertEquals("max(a, min(b, c))", rendered("max(a, min(b, c))"))
        assertEquals("f((+ a 1), (* b 2))", rendered("f(a + 1, b * 2)"))
    }

    @Test
    fun `string literal is an expression`() {
        assertEquals("\"hi\"", rendered("\"hi\""))
    }

    @Test
    fun `double and boolean literals are expressions`() {
        assertEquals("(+ 3.14 2)", rendered("3.14 + 2"))
        assertEquals("(&& true false)", rendered("true && false"))
        assertEquals("(* x 2.0)", rendered("x * 2.0"))
    }

    @Test
    fun `int and double are separated at parse time`() {
        val int = parse("3").getOrElse { error("unexpected: $it") }
        assertInstanceOf(IntLiteralExpressionNode::class.java, int)

        val double = parse("3.0").getOrElse { error("unexpected: $it") }
        assertInstanceOf(DoubleLiteralExpressionNode::class.java, double)
    }

    @Test
    fun `each literal kind maps to its own node`() {
        assertInstanceOf(StringLiteralExpressionNode::class.java, parse("\"s\"").getOrElse { error("$it") })
        assertInstanceOf(BooleanLiteralExpressionNode::class.java, parse("true").getOrElse { error("$it") })
        assertInstanceOf(BooleanLiteralExpressionNode::class.java, parse("false").getOrElse { error("$it") })
    }

    // region Arrays

    @Test
    fun `array access with literal index`() {
        assertEquals("arr[0]", rendered("arr[0]"))
        val node = parse("arr[0]").getOrElse { error("unexpected: $it") }
        val access = assertInstanceOf(ArrayAccessExpressionASTNode::class.java, node)
        assertEquals("arr", access.array)
        assertInstanceOf(IntLiteralExpressionNode::class.java, access.index)
        assertEquals(0L, (access.index as IntLiteralExpressionNode).value)
    }

    @Test
    fun `array access index respects expression precedence`() {
        assertEquals("arr[(+ i 1)]", rendered("arr[i + 1]"))
    }

    @Test
    fun `static array initialization list`() {
        assertEquals("[1, 2, 3]", rendered("[1, 2, 3]"))
        val node = parse("[1, 2, 3]").getOrElse { error("unexpected: $it") }
        val list = assertInstanceOf(StaticArrayInitializationExpressionsListNode::class.java, node)
        assertEquals(3, list.values.size)
        assertEquals(listOf(1L, 2L, 3L), list.values.map { (it as IntLiteralExpressionNode).value })
    }

    @Test
    fun `empty static array initialization list`() {
        assertEquals("[]", rendered("[]"))
        val list = assertInstanceOf(
            StaticArrayInitializationExpressionsListNode::class.java,
            parse("[]").getOrElse { error("unexpected: $it") },
        )
        assertTrue(list.values.isEmpty())
    }

    @Test
    fun `identifier followed by lparen is call not array access`() {
        val node = parse("f()").getOrElse { error("unexpected: $it") }
        assertInstanceOf(FunctionCallExpressionNode::class.java, node)
    }

    @Test
    fun `identifier followed by lbracket is array access not call`() {
        val node = parse("arr[0]").getOrElse { error("unexpected: $it") }
        assertInstanceOf(ArrayAccessExpressionASTNode::class.java, node)
    }

    // endregion

    // endregion

    // region Negative scenarios

    @Test
    fun `missing right operand fails`() {
        assertTrue(parse("3 +").isLeft())
    }

    @Test
    fun `missing left operand fails`() {
        assertTrue(parse("* 5").isLeft())
    }

    @Test
    fun `unclosed parenthesis fails`() {
        assertTrue(parse("(3 + 5").isLeft())
    }

    @Test
    fun `empty argument fails`() {
        assertTrue(parse("max(3,)").isLeft())
    }

    @Test
    fun `missing argument separator fails`() {
        assertTrue(parse("max(3 5)").isLeft())
    }

    @Test
    fun `truncated call fails`() {
        assertTrue(parse("max(").isLeft())
    }

    // region Arrays — negative

    @Test
    fun `unclosed array index fails`() {
        assertTrue(parse("arr[").isLeft())
    }

    @Test
    fun `empty array index fails`() {
        assertTrue(parse("arr[]").isLeft())
    }

    @Test
    fun `unclosed initialization list fails`() {
        assertTrue(parse("[1, 2").isLeft())
    }

    @Test
    fun `array access cannot be called`() {
        assertTrue(parse("arr[0](1)").isLeft())
    }

    @Test
    fun `array access with nested arithmetic index`() {
        assertEquals("arr[(+ i (* 2 1))]", rendered("arr[i + 2 * 1]"))
    }

    @Test
    fun `array access with unary minus applies to access result`() {
        assertEquals("(- arr[i])", rendered("-arr[i]"))
    }

    @Test
    fun `initialization list with single element`() {
        assertEquals("[42]", rendered("[42]"))
    }

    @Test
    fun `array access binds tighter than addition`() {
        assertEquals("(+ arr[0] 1)", rendered("arr[0] + 1"))
    }

    // endregion

    // endregion
}
