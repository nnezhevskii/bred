package org.nnezh

import org.junit.jupiter.api.Test
import org.nnezh.bred.ast.ArrayElementAccessASTNode
import org.nnezh.bred.ast.AssignmentStatementAstNode
import org.nnezh.bred.ast.BinaryExpressionASTNode
import org.nnezh.bred.ast.BlockAstNode
import org.nnezh.bred.ast.FunctionCallExpressionASTNode
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.ReturnFunctionStatementAstNode
import org.nnezh.bred.ast.ScalarVariableInitializationASTNode
import org.nnezh.bred.ast.VariableExpressionASTNode

class RootSemanticMigrationTest {
    @Test
    fun `migrated root semantic test - function calls support forward references and overloads`() {
        assertNoSemanticDiagnostics(
            """
            fun useBoth(): Unit {
                target(1)
                target("s")
            }
            fun target(value: Int): Unit { }
            fun target(value: String): Unit { }
            """.trimIndent(),
        )
    }

    @Test
    fun `migrated root semantic test - duplicate function signature is rejected`() {
        assertSingleSemanticError(
            """
            fun target(value: Int): Unit { }
            fun target(other: Int): String {
                return "duplicate"
            }
            """.trimIndent(),
            SemanticErrorType.REDEFINE_FUNCTION,
            FunctionDeclAstNode::class.java,
        )
    }

    @Test
    fun `migrated root semantic test - wrong function arity is rejected`() {
        assertSingleSemanticError(
            """
            fun pair(a: Int, b: Int): Unit { }
            fun main(): Unit {
                pair(1)
            }
            """.trimIndent(),
            SemanticErrorType.FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS,
            FunctionCallExpressionASTNode::class.java,
        )
    }

    @Test
    fun `migrated root semantic test - unknown function is rejected`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                missing(1)
            }
            """.trimIndent(),
            SemanticErrorType.FUNCTION_NOT_FOUND,
            FunctionCallExpressionASTNode::class.java,
        )
    }

    @Test
    fun `migrated root semantic test - function names are not first class values`() {
        assertSemanticError(
            """
            fun callback(): Unit { }
            fun invoke(value: Int): Unit { }
            fun main(): Unit {
                invoke(callback)
            }
            """.trimIndent(),
            SemanticErrorType.UNKNOWN_VARIABLE,
            VariableExpressionASTNode::class.java,
        )
    }

    @Test
    fun `migrated root semantic test - initialized locals are visible to later initializers`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val first: Int = 1
                val second: Int = first + 1
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `migrated root semantic test - forward local reference is rejected`() {
        assertSemanticError(
            """
            fun main(): Unit {
                val first: Int = second + 1
                val second: Int = 2
            }
            """.trimIndent(),
            SemanticErrorType.UNKNOWN_VARIABLE,
            VariableExpressionASTNode::class.java,
        )
    }

    @Test
    fun `migrated root semantic test - redeclaration in same block is rejected`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val value: Int = 1
                var value: Int = 2
            }
            """.trimIndent(),
            SemanticErrorType.VARIABLE_REDECLARATION,
            ScalarVariableInitializationASTNode::class.java,
        )
    }

    @Test
    fun `migrated root semantic test - nested shadowing remains a warning`() {
        assertSemanticWarning(
            """
            fun main(): Unit {
                val value: Int = 1
                if (true) {
                    val value: Int = 2
                }
            }
            """.trimIndent(),
            SemanticErrorType.VARIABLE_OVERSHADOW,
            ScalarVariableInitializationASTNode::class.java,
        )
    }

    @Test
    fun `migrated root semantic test - variable declared inside if is not visible outside`() {
        assertSemanticError(
            """
            fun main(): Unit {
                if (true) {
                    val inside: Int = 1
                }
                val copy: Int = inside
            }
            """.trimIndent(),
            SemanticErrorType.UNKNOWN_VARIABLE,
            VariableExpressionASTNode::class.java,
        )
    }

    @Test
    fun `migrated root semantic test - outer mutable variable remains visible through while`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                var counter: Int = 0
                while (counter < 2) {
                    counter = counter + 1
                }
                counter = counter + 1
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `migrated root semantic test - for counter is not visible after loop`() {
        assertSemanticError(
            """
            fun main(): Unit {
                var total: Int = 0
                for (i in 0 to 2) {
                    total = total + i
                }
                val copy: Int = i
            }
            """.trimIndent(),
            SemanticErrorType.UNKNOWN_VARIABLE,
            VariableExpressionASTNode::class.java,
        )
    }

    @Test
    fun `migrated root semantic test - immutable scalar assignment is rejected`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val value: Int = 1
                value = 2
            }
            """.trimIndent(),
            SemanticErrorType.VARIABLE_CHANGING_IMMUTABLE,
            AssignmentStatementAstNode::class.java,
        )
    }

    @Test
    fun `migrated root semantic test - assignment rhs type mismatch is rejected`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                var value: Int = 1
                value = "wrong"
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            AssignmentStatementAstNode::class.java,
        )
    }

    @Test
    fun `migrated root semantic test - arithmetic and logical operand types are checked`() {
        assertSemanticError(
            """
            fun main(): Unit {
                val bad: Int = 1 + true
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            BinaryExpressionASTNode::class.java,
        )

        assertSemanticError(
            """
            fun main(): Unit {
                val bad: Boolean = 1 && 2
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            BinaryExpressionASTNode::class.java,
        )
    }

    @Test
    fun `migrated root semantic test - return type and missing return are checked`() {
        assertSingleSemanticError(
            """
            fun answer(): Int {
                return "wrong"
            }
            """.trimIndent(),
            SemanticErrorType.METHOD_HAS_WRONG_RETURN,
            ReturnFunctionStatementAstNode::class.java,
        )

        assertSingleSemanticError(
            """
            fun answer(): Int { }
            """.trimIndent(),
            SemanticErrorType.EXPLICIT_RETURN_IS_EXPECTED,
            BlockAstNode::class.java,
        )
    }

    @Test
    fun `migrated root semantic test - array parameter can be read and passed`() {
        assertNoSemanticDiagnostics(
            """
            fun first(values: Int[]): Int {
                return values[0]
            }
            fun main(): Unit {
                val data: Int[2] = [1, 2]
                val hit: Int = first(data)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `migrated root semantic test - array read requires known array and int index`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val hit: Int = missing[0]
            }
            """.trimIndent(),
            SemanticErrorType.UNKNOWN_VARIABLE,
            ArrayElementAccessASTNode::class.java,
        )

        assertSingleSemanticError(
            """
            fun main(): Unit {
                val data: Int[1] = [1]
                val hit: Int = data[true]
            }
            """.trimIndent(),
            SemanticErrorType.ARRAY_INDEX_IS_NOT_INTEGER,
            ArrayElementAccessASTNode::class.java,
        )
    }

    @Test
    fun `migrated root semantic test - scalar cannot be read with array syntax`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val value: Int = 1
                val hit: Int = value[0]
            }
            """.trimIndent(),
            SemanticErrorType.ARRAY_IS_EXPECTED_BUT_GOT_SCALAR,
            ArrayElementAccessASTNode::class.java,
        )
    }

    @Test
    fun `migrated root semantic test - array element assignment checks value type`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val data: Int[1] = [1]
                data[0] = "wrong"
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            AssignmentStatementAstNode::class.java,
        )
    }
}
