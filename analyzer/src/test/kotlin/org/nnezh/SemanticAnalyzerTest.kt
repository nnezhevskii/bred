package org.nnezh

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.bred.ast.ArrayDeclarationASTNode
import org.nnezh.bred.ast.ArrayElementAccessASTNode
import org.nnezh.bred.ast.ArrayInitializationExpressionASTNode
import org.nnezh.bred.ast.AssignmentStatementAstNode
import org.nnezh.bred.ast.BinaryExpressionASTNode
import org.nnezh.bred.ast.BlockAstNode
import org.nnezh.bred.ast.FunctionCallExpressionASTNode
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.ReturnFunctionStatementAstNode
import org.nnezh.bred.ast.ScalarVariableInitializationASTNode
import org.nnezh.bred.ast.UnaryExpressionASTNode
import org.nnezh.bred.ast.VariableExpressionASTNode

class SemanticAnalyzerTest {
    // region Positive baseline

    @Test
    fun `empty program has no semantic diagnostics`() {
        assertNoSemanticDiagnostics("")
    }

    @Test
    fun `primitive local initializers have no semantic diagnostics`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val i: Int = 1
                val d: Double = 1.0
                val s: String = "bred"
                val b: Boolean = true
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `function arguments are visible in body`() {
        assertNoSemanticDiagnostics(
            """
            fun id(value: Int): Int {
                return value
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `forward function reference is valid`() {
        assertNoSemanticDiagnostics(
            """
            fun first(): Unit {
                second()
            }
            fun second(): Unit { }
            """.trimIndent(),
        )
    }

    @Test
    fun `int arithmetic and boolean comparison are valid`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val sum: Int = 1 + 2 * 3
                val ok: Boolean = sum > 0
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `boolean conditions are accepted in if and while`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                var keepGoing: Boolean = true
                if (keepGoing) {
                    println("if")
                }
                while (keepGoing) {
                    keepGoing = false
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `function call result type can initialize a variable`() {
        assertNoSemanticDiagnostics(
            """
            fun one(): Int {
                return 1
            }
            fun main(): Unit {
                val n: Int = one()
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `supported builtin calls have no semantic diagnostics`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val n: Int = readInt()
                val same: Boolean = stringEquals("a", "a")
                if (same) {
                    println("ok")
                }
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region Scope and variables

    @Test
    fun `unknown variable in initializer is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val n: Int = missing
            }
            """.trimIndent(),
            SemanticErrorType.UNKNOWN_VARIABLE,
            VariableExpressionASTNode::class.java,
        )
    }

    @Test
    fun `variable redeclaration in same block is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val n: Int = 1
                val n: Int = 2
            }
            """.trimIndent(),
            SemanticErrorType.VARIABLE_REDECLARATION,
            ScalarVariableInitializationASTNode::class.java,
        )
    }

    @Test
    fun `shadowing outer variable is reported as warning`() {
        assertSemanticWarning(
            """
            fun main(): Unit {
                val n: Int = 1
                if (true) {
                    val n: Int = 2
                }
            }
            """.trimIndent(),
            SemanticErrorType.VARIABLE_OVERSHADOW,
            ScalarVariableInitializationASTNode::class.java,
        )
    }

    @Test
    fun `global variable is visible in function body`() {
        assertNoSemanticDiagnostics(
            """
            val answer: Int = 42
            fun main(): Unit {
                val n: Int = answer
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `global variable initializer is type checked`() {
        assertSingleSemanticError(
            """
            val answer: Int = "wrong"
            fun main(): Unit { }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
        )
    }

    @Test
    fun `mutable variable assignment with matching type is valid`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                var n: Int = 1
                n = 2
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `assignment to immutable scalar val is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val n: Int = 1
                n = 2
            }
            """.trimIndent(),
            SemanticErrorType.VARIABLE_CHANGING_IMMUTABLE,
            AssignmentStatementAstNode::class.java,
        )
    }

    @Test
    fun `assignment with wrong rhs type is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                var n: Int = 1
                n = "wrong"
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            AssignmentStatementAstNode::class.java,
        )
    }

    // endregion

    // region Functions and overloads

    @Test
    fun `unknown function call is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                missing()
            }
            """.trimIndent(),
            SemanticErrorType.FUNCTION_NOT_FOUND,
            FunctionCallExpressionASTNode::class.java,
        )
    }

    @Test
    fun `function call with wrong argument type is a semantic error`() {
        assertSingleSemanticError(
            """
            fun takeInt(n: Int): Unit { }
            fun main(): Unit {
                takeInt("wrong")
            }
            """.trimIndent(),
            SemanticErrorType.FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS,
            FunctionCallExpressionASTNode::class.java,
        )
    }

    @Test
    fun `function overload is resolved by argument types`() {
        assertNoSemanticDiagnostics(
            """
            fun pick(n: Int): Int {
                return n
            }
            fun pick(s: String): String {
                return s
            }
            fun main(): Unit {
                val n: Int = pick(1)
                val s: String = pick("s")
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `duplicate function signature is a semantic error`() {
        assertSingleSemanticError(
            """
            fun pick(n: Int): Int {
                return n
            }
            fun pick(value: Int): String {
                return "duplicate"
            }
            """.trimIndent(),
            SemanticErrorType.REDEFINE_FUNCTION,
            FunctionDeclAstNode::class.java,
        )
    }

    @Test
    fun `function name used as value is not a first class function`() {
        assertSemanticError(
            """
            fun callback(): Unit { }
            fun takeInt(n: Int): Unit { }
            fun main(): Unit {
                takeInt(callback)
            }
            """.trimIndent(),
            SemanticErrorType.UNKNOWN_VARIABLE,
            VariableExpressionASTNode::class.java,
        )
    }

    // endregion

    // region Type checking

    @Test
    fun `initializer type mismatch is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val n: Int = "wrong"
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            ScalarVariableInitializationASTNode::class.java,
        )
    }

    @Test
    fun `mixed int and double arithmetic is rejected without promotion`() {
        assertSemanticError(
            """
            fun main(): Unit {
                val n: Int = 1 + 2.0
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            BinaryExpressionASTNode::class.java,
        )
    }

    @Test
    fun `logical operators require boolean operands`() {
        assertSemanticError(
            """
            fun main(): Unit {
                val bad: Boolean = 1 && 0
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            BinaryExpressionASTNode::class.java,
        )
    }

    @Test
    fun `unary not requires boolean operand`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val bad: Boolean = !1
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            UnaryExpressionASTNode::class.java,
        )
    }

    @Test
    fun `if condition must be boolean`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                if (1) {
                    println("bad")
                }
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
        )
    }

    @Test
    fun `while condition must be boolean`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                while ("bad") { }
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
        )
    }

    // endregion

    // region Arrays

    @Test
    fun `static array declaration without initializer is valid`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val values: Int[3]
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `static array initialization list with matching elements is valid`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val values: Int[3] = [1, 2, 3]
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `static array initialization list rejects mixed element types`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val values: Int[2] = [1, true]
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCONSISTENT_ARRAY_TYPE,
            ArrayInitializationExpressionASTNode::class.java,
        )
    }

    @Test
    fun `static array initialization list size must match declaration size`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val values: Int[2] = [1]
            }
            """.trimIndent(),
            SemanticErrorType.INVALID_AMOUNT_OF_ARGUMENTS_IN_ARRAYS_INITIALIZATION,
            ArrayDeclarationASTNode::class.java,
        )
    }

    @Test
    fun `array read with int index is valid`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val values: Int[2] = [1, 2]
                val first: Int = values[0]
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `array index must be int`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val values: Int[2] = [1, 2]
                val first: Int = values[true]
            }
            """.trimIndent(),
            SemanticErrorType.ARRAY_INDEX_IS_NOT_INTEGER,
            ArrayElementAccessASTNode::class.java,
        )
    }

    @Test
    fun `scalar variable used with array access is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val n: Int = 1
                val bad: Int = n[0]
            }
            """.trimIndent(),
            SemanticErrorType.ARRAY_IS_EXPECTED_BUT_GOT_SCALAR,
            ArrayElementAccessASTNode::class.java,
        )
    }

    @Test
    fun `array element assignment with matching element type is valid`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val values: Int[2] = [1, 2]
                values[0] = 3
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `array element assignment with incompatible value is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val values: Int[2] = [1, 2]
                values[0] = "bad"
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            AssignmentStatementAstNode::class.java,
        )
    }

    @Test
    fun `array parameter can be read and passed`() {
        assertNoSemanticDiagnostics(
            """
            fun first(values: Int[]): Int {
                return values[0]
            }
            fun main(): Unit {
                val values: Int[2] = [1, 2]
                val n: Int = first(values)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passing scalar where array parameter is expected is a semantic error`() {
        assertSingleSemanticError(
            """
            fun take(values: Int[]): Unit { }
            fun main(): Unit {
                take(1)
            }
            """.trimIndent(),
            SemanticErrorType.FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS,
            FunctionCallExpressionASTNode::class.java,
        )
    }

    // endregion

    // region Returns and control flow

    @Test
    fun `wrong return type is a semantic error`() {
        assertSingleSemanticError(
            """
            fun answer(): Int {
                return "wrong"
            }
            """.trimIndent(),
            SemanticErrorType.METHOD_HAS_WRONG_RETURN,
            ReturnFunctionStatementAstNode::class.java,
        )
    }

    @Test
    fun `non unit function requires explicit return`() {
        assertSingleSemanticError(
            """
            fun answer(): Int { }
            """.trimIndent(),
            SemanticErrorType.EXPLICIT_RETURN_IS_EXPECTED,
            BlockAstNode::class.java,
        )
    }

    @Test
    fun `code after return is a semantic error`() {
        assertSingleSemanticError(
            """
            fun answer(): Int {
                return 1
                val unreachable: Int = 2
            }
            """.trimIndent(),
            SemanticErrorType.BLOCK_CONTAINS_CODE_AFTER_RETURN,
            BlockAstNode::class.java,
        )
    }

    @Test
    fun `multiple returns in one block are a semantic error`() {
        assertSingleSemanticError(
            """
            fun answer(): Int {
                return 1
                return 2
            }
            """.trimIndent(),
            SemanticErrorType.BLOCK_CONTAINS_MORE_THAN_ONE_RETURN,
            BlockAstNode::class.java,
        )
    }

    // endregion

    // region Public pipeline and TODO regression coverage

    @Test
    fun `for loop desugared variables are scoped and typed`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                var total: Int = 0
                for (i in 0 to 2) {
                    total = total + i
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `raw typeclass declarations are accepted before template instantiation`() {
        assertTrue(
            parseEither(
                """
                typeclass Printable<A> {
                    fun print(a: A): Unit
                }
                """.trimIndent(),
            ).isRight(),
        )
    }

    // endregion
}
