package org.nnezh.org.nnezh.semantic

import arrow.core.getOrElse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.ast.AbstractSyntaxTreeBuilder
import org.nnezh.ast.AssignmentStatementASTNode
import org.nnezh.ast.ArrayAccessExpressionASTNode
import org.nnezh.ast.lvalueName
import org.nnezh.ast.FunctionArgumentASTNode
import org.nnezh.ast.ProgramASTNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.ast.VariableInitializationASTNode
import org.nnezh.lexer.Lexer
import org.nnezh.org.nnezh.semantic.analyzers.VariableScopeSubAnalyzer
import org.nnezh.org.nnezh.semantic.generic.SemanticError
import org.nnezh.org.nnezh.semantic.generic.SemanticErrorType

class VariableScopeAnalyzerTest {
    private val SemanticError.variableScope: SemanticError.VariableScopeSemanticError
        get() = this as SemanticError.VariableScopeSemanticError

    private fun analyze(src: String): List<SemanticError> {
        val tokens = Lexer(src).tokenize().getOrElse { error("unexpected lexer error: $it") }
        val ast = AbstractSyntaxTreeBuilder().build(tokens).getOrElse { error("unexpected parse error: $it") }
        val program = assertInstanceOf(ProgramASTNode::class.java, ast)
        return VariableScopeSubAnalyzer()(program)
    }

    @Test
    fun `unknown variable in expression is critical`() {
        val errors = analyze(
            """
            fun main(): Unit {
                return b
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.UNKNOWN_VARIABLE, errors.single().variableScope.errorType)
        assertTrue(errors.single().isCriticalError)
        val where = assertInstanceOf(VariableExpressionNode::class.java, errors.single().variableScope.where)
        assertEquals("b", where.token.lexeme)
    }

    @Test
    fun `unknown variable in assignment is critical`() {
        val errors = analyze(
            """
            fun main(): Unit {
                x = 1
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.UNKNOWN_VARIABLE, errors.single().variableScope.errorType)
        assertTrue(errors.single().isCriticalError)
        val where = assertInstanceOf(AssignmentStatementASTNode::class.java, errors.single().variableScope.where)
        assertEquals("x", where.lvalueName())
    }

    @Test
    fun `unknown variable in initializer is unknown variable not uninitialized`() {
        val errors = analyze(
            """
            fun main(): Unit {
                val a: Int = b + 1
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.UNKNOWN_VARIABLE, errors.single().variableScope.errorType)
        assertTrue(errors.single().isCriticalError)
        val where = assertInstanceOf(VariableExpressionNode::class.java, errors.single().variableScope.where)
        assertEquals("b", where.token.lexeme)
    }

    @Test
    fun `initialized variable use in later initializer is ok`() {
        val errors = analyze(
            """
            fun main(): Unit {
                val a: Int = 1
                val b: Int = a + 1
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `unknown variables in initializer are all reported as unknown`() {
        val errors = analyze(
            """
            val globalSeedValue: Int = 5
            fun foo(): Unit {
                val computedResult: Int = leftOperand + rightOperand + missingDependency
            }
            """.trimIndent(),
        )

        assertEquals(3, errors.size)
        assertTrue(errors.all { it.variableScope.errorType == SemanticErrorType.UNKNOWN_VARIABLE })
        assertTrue(errors.all { it.isCriticalError })
        val names = errors.map { assertInstanceOf(VariableExpressionNode::class.java, it.variableScope.where).token.lexeme }.toSet()
        assertEquals(setOf("leftOperand", "rightOperand", "missingDependency"), names)
    }

    @Test
    fun `returns all errors found inside statement but stops after critical statement`() {
        val errors = analyze(
            """
            val globalSeedValue: Int = 5
            fun foo(): Unit {
                val x: Int = globalSeedValue + 1
                val globalSeedValue: Int = leftOperand + rightOperand + missingDependency
                val unusedResult: Int = anotherMissing + x
            }
            """.trimIndent(),
        )

        // second statement:
        // - VARIABLE_OVERSHADOW (globalSeedValue shadowed)
        // - three UNKNOWN_VARIABLE from initializer (leftOperand, rightOperand, missingDependency)
        //
        // third statement should not be analyzed after critical error in second statement.
        assertEquals(4, errors.size)

        val types = errors.map { it.variableScope.errorType }.toSet()
        assertEquals(setOf(SemanticErrorType.VARIABLE_OVERSHADOW, SemanticErrorType.UNKNOWN_VARIABLE), types)
        assertTrue(errors.all { it.isCriticalError })

        val overshadow = errors.firstOrNull { it.variableScope.errorType == SemanticErrorType.VARIABLE_OVERSHADOW }
            ?: error("expected a VARIABLE_OVERSHADOW error")
        val overshadowWhere = assertInstanceOf(VariableInitializationASTNode::class.java, overshadow.variableScope.where)
        assertEquals("globalSeedValue", overshadowWhere.variableName)

        val unknownNames = errors
            .filter { it.variableScope.errorType == SemanticErrorType.UNKNOWN_VARIABLE }
            .map { assertInstanceOf(VariableExpressionNode::class.java, it.variableScope.where).token.lexeme }
            .toSet()
        assertEquals(setOf("leftOperand", "rightOperand", "missingDependency"), unknownNames)
    }

    @Test
    fun `mutable variable initializer can use global constant and parameter`() {
        val errors = analyze(
            """
            val pi: Double = 3.14
            fun calc(radius: Double): Int {
                var circumferenceLength: Double = 2 * pi * radius
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `assignment reports all unknown variables in expression`() {
        val errors = analyze(
            """
            val pi: Double = 3.14
            fun calc(radius: Double): Int {
                var circumferenceLength: Double = 2 * pi * radius
                circumferenceLength = 3 * length + tail - radius
            }
            """.trimIndent(),
        )

        assertEquals(2, errors.size)
        assertTrue(errors.all { it.variableScope.errorType == SemanticErrorType.UNKNOWN_VARIABLE })
        assertTrue(errors.all { it.isCriticalError })
        val unknownNames = errors
            .map { assertInstanceOf(VariableExpressionNode::class.java, it.variableScope.where).token.lexeme }
            .toSet()
        assertEquals(setOf("length", "tail"), unknownNames)
    }

    @Test
    fun `calc function reports only unknown identifiers in invalid reassignment`() {
        val errors = analyze(
            """
            val pi: Double = 3.14
            fun calc(radius: Double): Int {
                var circumferenceLength: Double = 2 * pi * radius
                circumferenceLength = 3 * length + tail - radius
                circumferenceLength = unknownAfterBadStatement + 1
            }
            """.trimIndent(),
        )

        // valid initializer, then bad assignment with two unknowns; third statement must not be analyzed.
        assertEquals(2, errors.size)
        assertTrue(errors.all { it.variableScope.errorType == SemanticErrorType.UNKNOWN_VARIABLE })
        assertTrue(errors.all { it.isCriticalError })
        val unknownNames = errors
            .map { assertInstanceOf(VariableExpressionNode::class.java, it.variableScope.where).token.lexeme }
            .toSet()
        assertEquals(setOf("length", "tail"), unknownNames)
    }

    @Test
    fun `function call reports all unknown variables in arguments`() {
        val errors = analyze(
            """
            fun main(): Unit {
                println(unknownFirst, unknownSecond)
            }
            """.trimIndent(),
        )

        assertEquals(2, errors.size)
        assertTrue(errors.all { it.variableScope.errorType == SemanticErrorType.UNKNOWN_VARIABLE })
        assertTrue(errors.all { it.isCriticalError })
        val unknownNames = errors
            .map { assertInstanceOf(VariableExpressionNode::class.java, it.variableScope.where).token.lexeme }
            .toSet()
        assertEquals(setOf("unknownFirst", "unknownSecond"), unknownNames)
    }

    @Test
    fun `mutable variable can be reassigned after initialization`() {
        val errors = analyze(
            """
            fun main(): Unit {
                var counter: Int = 0
                counter = counter + 1
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `critical error in if condition prevents analyzing then block`() {
        val errors = analyze(
            """
            fun main(): Unit {
                if (missingCondition) {
                    val unreachable: Int = anotherMissing
                }
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.UNKNOWN_VARIABLE, errors.single().variableScope.errorType)
        assertTrue(errors.single().isCriticalError)
        val where = assertInstanceOf(VariableExpressionNode::class.java, errors.single().variableScope.where)
        assertEquals("missingCondition", where.token.lexeme)
    }

    @Test
    fun `redeclaring name in same scope is critical`() {
        val errors = analyze(
            """
            fun main(): Unit {
                val a: Int = 1
                val a: Int = 2
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.VARIABLE_REDECLARATION, errors.single().variableScope.errorType)
        assertTrue(errors.single().isCriticalError)
        val where = assertInstanceOf(VariableInitializationASTNode::class.java, errors.single().variableScope.where)
        assertEquals("a", where.variableName)
    }

    @Test
    fun `shadowing variable in nested block is critical`() {
        val errors = analyze(
            """
            fun main(): Unit {
                val a: Int = 1
                if (true) {
                    val a: Int = 2
                }
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.VARIABLE_OVERSHADOW, errors.single().variableScope.errorType)
        assertTrue(errors.single().isCriticalError)
        val where = assertInstanceOf(VariableInitializationASTNode::class.java, errors.single().variableScope.where)
        assertEquals("a", where.variableName)
    }

    @Test
    fun `shadowing function argument over global is critical`() {
        val errors = analyze(
            """
            val Pi: Double = 3.14
            fun calc(Pi: Double): Unit { }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.VARIABLE_OVERSHADOW, errors.single().variableScope.errorType)
        assertTrue(errors.single().isCriticalError)
        val where = assertInstanceOf(FunctionArgumentASTNode::class.java, errors.single().variableScope.where)
        assertEquals("Pi", where.name)
    }

    @Test
    fun `shadowing local variable over global is critical`() {
        val errors = analyze(
            """
            val Pi: Double = 3.14
            fun main(): Unit {
                val Pi: Double = 4.0
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.VARIABLE_OVERSHADOW, errors.single().variableScope.errorType)
        assertTrue(errors.single().isCriticalError)
        val where = assertInstanceOf(VariableInitializationASTNode::class.java, errors.single().variableScope.where)
        assertEquals("Pi", where.variableName)
    }

    @Test
    fun `variable from outer scope is visible inside nested block`() {
        val errors = analyze(
            """
            fun main(): Unit {
                val a: Int = 1
                if (true) {
                    val b: Int = a
                }
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `variable declared in if block is not visible outside`() {
        val errors = analyze(
            """
            fun main(): Unit {
                if (true) {
                    val a: Int = 1
                }
                return a
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.UNKNOWN_VARIABLE, errors.single().variableScope.errorType)
        assertTrue(errors.single().isCriticalError)
        val where = assertInstanceOf(VariableExpressionNode::class.java, errors.single().variableScope.where)
        assertEquals("a", where.token.lexeme)
    }

    @Test
    fun `variable declared in while body is not visible outside`() {
        val errors = analyze(
            """
            fun main(): Unit {
                while (false) {
                    val a: Int = 1
                }
                return a
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.UNKNOWN_VARIABLE, errors.single().variableScope.errorType)
        assertTrue(errors.single().isCriticalError)
        val where = assertInstanceOf(VariableExpressionNode::class.java, errors.single().variableScope.where)
        assertEquals("a", where.token.lexeme)
    }

    @Test
    fun `for counter variable is not visible after loop`() {
        val errors = analyze(
            """
            fun main(): Unit {
                for (i in 0 to 10) {
                    println(i)
                }
                println(i)
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.UNKNOWN_VARIABLE, errors.single().variableScope.errorType)
        assertTrue(errors.single().isCriticalError)
        val where = assertInstanceOf(VariableExpressionNode::class.java, errors.single().variableScope.where)
        assertEquals("i", where.token.lexeme)
    }

    // region Arrays

    @Test
    fun `unknown array in read is critical`() {
        val errors = analyze(
            """
            fun main(): Unit {
                return arr[0]
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.UNKNOWN_VARIABLE, errors.single().variableScope.errorType)
        val where = assertInstanceOf(ArrayAccessExpressionASTNode::class.java, errors.single().variableScope.where)
        assertEquals("arr", where.array)
    }

    @Test
    fun `unknown array in assignment is critical`() {
        val errors = analyze(
            """
            fun main(): Unit {
                arr[0] = 1
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(SemanticErrorType.UNKNOWN_VARIABLE, errors.single().variableScope.errorType)
        assertInstanceOf(AssignmentStatementASTNode::class.java, errors.single().variableScope.where)
    }

    @Test
    fun `known static array allows element assignment`() {
        val errors = analyze(
            """
            fun main(): Unit {
                val arr: Int[2]
                arr[0] = 1
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `array index may reference outer variable`() {
        val errors = analyze(
            """
            fun main(): Unit {
                val arr: Int[2]
                val i: Int = 0
                arr[i] = 1
            }
            """.trimIndent(),
        )

        assertTrue(errors.isEmpty())
    }

    // endregion
}
