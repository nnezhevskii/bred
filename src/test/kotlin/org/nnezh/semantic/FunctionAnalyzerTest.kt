package org.nnezh.org.nnezh.semantic

import arrow.core.getOrElse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.ast.AbstractSyntaxTreeBuilder
import org.nnezh.ast.FunctionCallExpressionNode
import org.nnezh.ast.ProgramASTNode
import org.nnezh.lexer.Lexer
import org.nnezh.org.nnezh.semantic.analyzers.FunctionSubAnalyzer
import org.nnezh.org.nnezh.semantic.generic.SemanticError
import org.nnezh.org.nnezh.semantic.generic.SemanticErrorType

class FunctionAnalyzerTest {
    private val SemanticError.functionSemantic: SemanticError.FunctionSemanticError
        get() = this as SemanticError.FunctionSemanticError

    private fun analyze(src: String): List<SemanticError> {
        val tokens = Lexer(src).tokenize().getOrElse { error("unexpected lexer error: $it") }
        val ast = AbstractSyntaxTreeBuilder().build(tokens).getOrElse { error("unexpected parse error: $it") }
        val program = assertInstanceOf(ProgramASTNode::class.java, ast)
        return FunctionSubAnalyzer()(program)
    }

    // region Positive scenarios

    @Test
    fun `valid call with matching arity`() {
        val errors = analyze(
            """
            fun moo(x: Int, y: Int): Unit {
                moo(3, 2)
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `argument types are not checked`() {
        val errors = analyze(
            """
            fun moo(x: Int, y: Int): Unit {
                moo("x", true)
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `overload by arity is allowed`() {
        val errors = analyze(
            """
            fun foo(a: Int): Unit { }
            fun foo(a: Int, b: Int): Unit {
                foo(1)
                foo(1, 2)
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `forward reference between functions`() {
        val errors = analyze(
            """
            fun a(): Unit {
                b()
            }
            fun b(): Unit { }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `builtin call is valid`() {
        val errors = analyze(
            """
            fun main(): Unit {
                println("hi")
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `call in assignment RHS`() {
        val errors = analyze(
            """
            fun add(a: Int, b: Int): Int {
                return a
            }
            fun main(): Unit {
                val x: Int = add(1, 2)
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `nested call in arguments`() {
        val errors = analyze(
            """
            fun bar(a: Int): Int {
                return a
            }
            fun foo(a: Int, b: Int): Int {
                return a
            }
            fun main(): Unit {
                foo(bar(1), 2)
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `statement-level call`() {
        val errors = analyze(
            """
            fun main(): Unit {
                println("ok")
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    // endregion

    // region Negative — calls

    @Test
    fun `unknown function is critical`() {
        val errors = analyze(
            """
            fun main(): Unit {
                missing(1)
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.FUNCTION_NOT_FOUND, errors.single().functionSemantic.errorType)
        assertTrue(errors.single().isCriticalError)
        val where = assertInstanceOf(FunctionCallExpressionNode::class.java, errors.single().functionSemantic.where)
        assertEquals("missing", where.name.lexeme)
    }

    @Test
    fun `too few arguments`() {
        val errors = analyze(
            """
            fun foo(a: Int, b: Int): Unit { }
            fun main(): Unit {
                foo(1)
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS_AMOUNT, errors.single().functionSemantic.errorType)
        assertTrue(errors.single().isCriticalError)
        val where = assertInstanceOf(FunctionCallExpressionNode::class.java, errors.single().functionSemantic.where)
        assertEquals("foo", where.name.lexeme)
    }

    @Test
    fun `too many arguments`() {
        val errors = analyze(
            """
            fun foo(a: Int, b: Int): Unit { }
            fun main(): Unit {
                foo(1, 2, 3)
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS_AMOUNT, errors.single().functionSemantic.errorType)
        assertTrue(errors.single().isCriticalError)
        val where = assertInstanceOf(FunctionCallExpressionNode::class.java, errors.single().functionSemantic.where)
        assertEquals("foo", where.name.lexeme)
    }

    @Test
    fun `zero args when one required`() {
        val errors = analyze(
            """
            fun foo(x: Int): Unit { }
            fun main(): Unit {
                foo()
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS_AMOUNT, errors.single().functionSemantic.errorType)
        assertTrue(errors.single().isCriticalError)
        val where = assertInstanceOf(FunctionCallExpressionNode::class.java, errors.single().functionSemantic.where)
        assertEquals("foo", where.name.lexeme)
    }

    @Test
    fun `builtin wrong arity`() {
        val errors = analyze(
            """
            fun main(): Unit {
                println(1, 2)
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS_AMOUNT, errors.single().functionSemantic.errorType)
        assertTrue(errors.single().isCriticalError)
        val where = assertInstanceOf(FunctionCallExpressionNode::class.java, errors.single().functionSemantic.where)
        assertEquals("println", where.name.lexeme)
    }

    // endregion

    // region Negative — REDEFINE_FUNCTION

    @Test
    fun `duplicate user function same arity`() {
        val errors = analyze(
            """
            fun foo(a: Int): Unit { }
            fun foo(b: Int): Unit { }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.REDEFINE_FUNCTION, errors.single().functionSemantic.errorType)
        assertTrue(errors.single().isCriticalError)
        assertInstanceOf(ProgramASTNode::class.java, errors.single().functionSemantic.where)
    }

    @Test
    fun `redefine builtin with same arity`() {
        val errors = analyze(
            """
            fun println(x: String): Unit { }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.REDEFINE_FUNCTION, errors.single().functionSemantic.errorType)
        assertTrue(errors.single().isCriticalError)
        assertInstanceOf(ProgramASTNode::class.java, errors.single().functionSemantic.where)
    }

    @Test
    fun `different arity is not redefine`() {
        val errors = analyze(
            """
            fun foo(a: Int): Unit { }
            fun foo(a: Int, b: Int): Unit { }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `overload by parameter types is allowed`() {
        val errors = analyze(
            """
            fun foo(x: Int): Unit { }
            fun foo(x: String): Unit { }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `duplicate same parameter types different return type is redefine`() {
        val errors = analyze(
            """
            fun foo(x: String): Unit { }
            fun foo(x: String): String {
                return x
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.REDEFINE_FUNCTION, errors.single().functionSemantic.errorType)
        assertTrue(errors.single().isCriticalError)
        assertInstanceOf(ProgramASTNode::class.java, errors.single().functionSemantic.where)
    }

    @Test
    fun `duplicate same parameter types implicit Unit and explicit return type is redefine`() {
        val errors = analyze(
            """
            fun foo(x: String) { }
            fun foo(x: String): String {
                return x
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.REDEFINE_FUNCTION, errors.single().functionSemantic.errorType)
        assertTrue(errors.single().isCriticalError)
        assertInstanceOf(ProgramASTNode::class.java, errors.single().functionSemantic.where)
    }

    // endregion

    // region Positive — function and variable same name

    @Test
    fun `global val and function with same name`() {
        val errors = analyze(
            """
            fun foo(): Unit { }
            val foo: Int = 1
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `local val and function with same name`() {
        val errors = analyze(
            """
            fun foo(): Unit { }
            fun main(): Unit {
                val foo: Int = 1
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `global val and builtin with same name`() {
        val errors = analyze(
            """
            val println: Int = 1
            fun main(): Unit {
                println("ok")
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `var assignment shares name with function`() {
        val errors = analyze(
            """
            fun foo(): Unit { }
            fun main(): Unit {
                var foo: Int = 1
                foo = 2
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `bare identifier in expression is not function analyzer concern`() {
        val errors = analyze(
            """
            fun foo(): Unit { }
            fun main(): Unit {
                val x: Int = foo + 1
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `variable reference when both function and variable exist`() {
        val errors = analyze(
            """
            fun foo(): Unit { }
            val foo: Int = 1
            fun main(): Unit {
                val x: Int = foo + 1
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `function call when variable with same name exists`() {
        val errors = analyze(
            """
            fun foo(): Unit { }
            val foo: Int = 1
            fun main(): Unit {
                foo()
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `variable as call argument when name collides with function`() {
        val errors = analyze(
            """
            fun foo(): Unit { }
            fun bar(x: Int): Unit { }
            val foo: Int = 1
            fun main(): Unit {
                bar(foo)
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `call and variable use in same block`() {
        val errors = analyze(
            """
            fun foo(): Unit { }
            val foo: Int = 10
            fun main(): Unit {
                val x: Int = foo
                foo()
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    // endregion

    // region Behavioral / regression

    @Test
    fun `wrong arity and unknown args in same call both reported`() {
        val errors = analyze(
            """
            fun foo(a: Int, b: Int): Unit { }
            fun main(): Unit {
                foo(unknown)
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS_AMOUNT, errors.single().functionSemantic.errorType)
        assertTrue(errors.single().isCriticalError)
        val where = assertInstanceOf(FunctionCallExpressionNode::class.java, errors.single().functionSemantic.where)
        assertEquals("foo", where.name.lexeme)
    }

    @Test
    fun `redefine stops entire program analysis early`() {
        val errors = analyze(
            """
            fun foo(a: Int): Unit { }
            fun foo(b: Int): Unit { }
            fun bar(): Unit {
                missing()
            }
            val x: Int = 1
            """.trimIndent(),
        )

        // Current behavior: REDEFINE_FUNCTION aborts before analyzing bar() or global val x.
        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.REDEFINE_FUNCTION, errors.single().functionSemantic.errorType)
        assertTrue(errors.single().isCriticalError)
        assertInstanceOf(ProgramASTNode::class.java, errors.single().functionSemantic.where)
    }

    // endregion
}
