package org.nnezh.bred.ast

import arrow.core.getOrElse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.bred.common.TypeSign
import org.nnezh.lexer.Lexer

class RootParserMigrationTest {
    private fun parseEither(source: String) =
        Lexer(source)
            .tokenize()
            .getOrElse { error("unexpected lexer error: $it") }
            .let { AbstractSyntaxTreeBuilder().build(it) }

    private fun parse(source: String): ProgramRoot =
        assertInstanceOf(ProgramRoot::class.java, parseEither(source).getOrElse { error("unexpected parser error: $it") })

    private fun parseFails(source: String): Boolean = parseEither(source).isLeft()

    @Test
    fun `migrated root parser test - full program keeps top level declarations`() {
        val root = parse(
            """
            val answer: Int = 42
            fun helper(value: Int): Int {
                return value
            }
            fun main(): Unit {
                val local: Int = helper(answer)
            }
            """.trimIndent(),
        )

        assertEquals(listOf("helper", "main"), root.functions.map { it.name })
        assertEquals("answer", root.globalVariables.single().name)
        assertEquals(TypeSign("Int"), root.globalVariables.single().type)
    }

    @Test
    fun `migrated root parser test - scalar declarations keep mutability and initializer expression`() {
        val root = parse(
            """
            fun main(): Unit {
                val immutable: Int = 1 + 2
                var mutable: String = "ok"
            }
            """.trimIndent(),
        )
        val declarations = root.functions.single().body.statements.filterIsInstance<ScalarVariableInitializationASTNode>()

        assertEquals(2, declarations.size)
        assertFalse(declarations[0].isMutable)
        assertTrue(declarations[1].isMutable)
        assertInstanceOf(BinaryExpressionASTNode::class.java, declarations[0].expression)
        assertEquals(TypeSign("String"), declarations[1].type)
    }

    @Test
    fun `migrated root parser test - assignment supports scalar and array lvalues`() {
        val root = parse(
            """
            fun main(): Unit {
                x = 1
                values[i + 1] = x
            }
            """.trimIndent(),
        )
        val statements = root.functions.single().body.statements.filterIsInstance<AssignmentStatementAstNode>()

        assertEquals(2, statements.size)
        val scalar = assertInstanceOf(VariableExpressionASTNode::class.java, statements[0].lValue)
        assertEquals("x", scalar.token.lexeme)
        val array = assertInstanceOf(ArrayElementAccessASTNode::class.java, statements[1].lValue)
        assertEquals("values", array.name)
        assertInstanceOf(BinaryExpressionASTNode::class.java, array.index)
    }

    @Test
    fun `migrated root parser test - expression precedence and associativity are preserved`() {
        val root = parse("fun main(): Unit { val value: Int = 1 + 2 * 3 - 4 }")
        val declaration = assertInstanceOf(
            ScalarVariableInitializationASTNode::class.java,
            root.functions.single().body.statements.first(),
        )
        val minus = assertInstanceOf(BinaryExpressionASTNode::class.java, declaration.expression)
        val plus = assertInstanceOf(BinaryExpressionASTNode::class.java, minus.left)
        val multiply = assertInstanceOf(BinaryExpressionASTNode::class.java, plus.right)

        assertEquals(BinaryOperator.Minus, minus.operator.kind)
        assertEquals(BinaryOperator.Plus, plus.operator.kind)
        assertEquals(BinaryOperator.Star, multiply.operator.kind)
    }

    @Test
    fun `migrated root parser test - unary expression binds tighter than binary expression`() {
        val root = parse("fun main(): Unit { val value: Int = -x + 1 }")
        val declaration = assertInstanceOf(
            ScalarVariableInitializationASTNode::class.java,
            root.functions.single().body.statements.first(),
        )
        val plus = assertInstanceOf(BinaryExpressionASTNode::class.java, declaration.expression)
        val unary = assertInstanceOf(UnaryExpressionASTNode::class.java, plus.left)

        assertEquals(UnaryOperator.Minus, unary.operator.kind)
        assertInstanceOf(VariableExpressionASTNode::class.java, unary.operand)
    }

    @Test
    fun `migrated root parser test - function calls parse as expressions and statements`() {
        val root = parse(
            """
            fun main(): Unit {
                println("hello")
                val value: Int = add(1, readInt())
            }
            """.trimIndent(),
        )
        val statements = root.functions.single().body.statements

        val callStatement = assertInstanceOf(CallFunctionStatementAstNode::class.java, statements[0])
        assertEquals("println", assertInstanceOf(FunctionCallExpressionASTNode::class.java, callStatement.expression).name)
        val declaration = assertInstanceOf(ScalarVariableInitializationASTNode::class.java, statements[1])
        val call = assertInstanceOf(FunctionCallExpressionASTNode::class.java, declaration.expression)
        assertEquals("add", call.name)
        assertInstanceOf(FunctionCallExpressionASTNode::class.java, call.arguments[1])
    }

    @Test
    fun `migrated root parser test - if else and while keep block shape`() {
        val root = parse(
            """
            fun main(): Unit {
                if (flag) { println("yes") } else { println("no") }
                while (flag) { tick() }
            }
            """.trimIndent(),
        )
        val statements = root.functions.single().body.statements

        val ifStatement = assertInstanceOf(IfStatementAstNode::class.java, statements[0])
        assertNotNull(ifStatement.elseBlock)
        assertEquals(1, ifStatement.thenBlock.statements.size)
        assertEquals(1, ifStatement.elseBlock?.statements?.size)
        val whileStatement = assertInstanceOf(WhileStatementAstNode::class.java, statements[1])
        assertEquals(1, whileStatement.bodyBlock.statements.size)
    }

    @Test
    fun `migrated root parser test - if without else uses nullable else block`() {
        val root = parse("fun main(): Unit { if (flag) { println(\"yes\") } }")
        val ifStatement = assertInstanceOf(IfStatementAstNode::class.java, root.functions.single().body.statements.first())

        assertNull(ifStatement.elseBlock)
    }

    @Test
    fun `migrated root parser test - returns distinguish explicit value from synthetic unit`() {
        val explicit = parse("fun answer(): Int { return 1 }")
        val explicitReturn = assertInstanceOf(ReturnFunctionStatementAstNode::class.java, explicit.functions.single().body.statements.single())

        assertTrue(explicitReturn.explicit)
        assertInstanceOf(IntLiteralExpressionASTNode::class.java, explicitReturn.expression)

        val synthetic = parse("fun main(): Unit { }")
        val syntheticReturn = assertInstanceOf(ReturnFunctionStatementAstNode::class.java, synthetic.functions.single().body.statements.single())
        assertFalse(syntheticReturn.explicit)
        assertNull(syntheticReturn.expression)
    }

    @Test
    fun `migrated root parser test - for loop is desugared into local declarations and while`() {
        val root = parse("fun main(): Unit { for (i in 0 to 2) { println(i) } }")
        val forStatement = assertInstanceOf(ForStatementAstNode::class.java, root.functions.single().body.statements.first())

        assertEquals(3, forStatement.desugaredContent.statements.size)
        assertInstanceOf(ScalarVariableInitializationASTNode::class.java, forStatement.desugaredContent.statements[0])
        assertInstanceOf(ScalarVariableInitializationASTNode::class.java, forStatement.desugaredContent.statements[1])
        assertInstanceOf(WhileStatementAstNode::class.java, forStatement.desugaredContent.statements[2])
    }

    @Test
    fun `migrated root parser test - static arrays parse declaration and initializer list`() {
        val root = parse(
            """
            fun main(): Unit {
                val empty: Int[3]
                val filled: Int[3] = [1, 2, 3]
            }
            """.trimIndent(),
        )
        val arrays = root.functions.single().body.statements.filterIsInstance<ArrayDeclarationASTNode>()

        assertEquals(2, arrays.size)
        assertEquals(3, arrays[0].size)
        assertNull(arrays[0].expression)
        val initializer = assertInstanceOf(ArrayInitializationExpressionASTNode::class.java, arrays[1].expression)
        assertEquals(3, initializer.args.size)
    }

    @Test
    fun `migrated root parser test - unsupported old root parser negative cases still fail`() {
        assertTrue(parseFails("fun main(): Unit { x + 1 = 2 }"))
        assertTrue(parseFails("fun main(): Unit { (f)() }"))
        assertTrue(parseFails("fun main(): Unit { if (true) { } else if (false) { } }"))
        assertTrue(parseFails("val xs: Int[n]"))
        assertTrue(parseFails("val xs: Int[3] = [1, 2,]"))
    }
}
