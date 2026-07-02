package org.nnezh

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SemanticDiagnosticFormatterTest {
    private val formatter = SemanticDiagnosticFormatter()

    @Test
    fun `formats unknown variable with name`() {
        val message = firstError(
            """
            fun main(): Unit {
                val n: Int = missing
            }
            """.trimIndent(),
        )

        assertTrue(message.contains("Semantic error UNKNOWN_VARIABLE"), message)
        assertTrue(message.contains("unknown variable 'missing'"), message)
    }

    @Test
    fun `formats scalar initializer type mismatch with expected and actual types`() {
        val message = firstError(
            """
            fun main(): Unit {
                val n: Int = "wrong"
            }
            """.trimIndent(),
        )

        assertTrue(message.contains("TYPE_CHECKER_INCOMPATIBLE_TYPES"), message)
        assertTrue(message.contains("initialize 'n: Int'"), message)
        assertTrue(message.contains("expected Int, got String"), message)
    }

    @Test
    fun `formats wrong function arguments with candidate signatures`() {
        val message = firstError(
            """
            fun takeInt(n: Int): Unit { }
            fun main(): Unit {
                takeInt("wrong")
            }
            """.trimIndent(),
        )

        assertTrue(message.contains("FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS"), message)
        assertTrue(message.contains("no overload of 'takeInt' accepts (String)"), message)
        assertTrue(message.contains("takeInt(Int): Unit"), message)
    }

    @Test
    fun `formats wrong return type`() {
        val message = firstError(
            """
            fun answer(): Int {
                return "wrong"
            }
            """.trimIndent(),
        )

        assertTrue(message.contains("METHOD_HAS_WRONG_RETURN"), message)
        assertTrue(message.contains("expected Int, got String"), message)
    }

    @Test
    fun `formats array initializer size mismatch`() {
        val message = firstError(
            """
            fun main(): Unit {
                val values: Int[2] = [1, 2, 3]
            }
            """.trimIndent(),
        )

        assertTrue(message.contains("INVALID_AMOUNT_OF_ARGUMENTS_IN_ARRAYS_INITIALIZATION"), message)
        assertTrue(message.contains("array 'values' expects 2 initializer element(s), got 3"), message)
    }

    @Test
    fun `formats scalar used as array`() {
        val message = firstError(
            """
            fun main(): Unit {
                val n: Int = 1
                val bad: Int = n[0]
            }
            """.trimIndent(),
        )

        assertTrue(message.contains("ARRAY_IS_EXPECTED_BUT_GOT_SCALAR"), message)
        assertTrue(message.contains("variable 'n' must be an array for indexed access, got Int"), message)
    }

    private fun firstError(source: String): String =
        analyze(source).fold(
            ifLeft = { errors -> formatter.format(errors.single()) },
            ifRight = { warnings -> error("expected semantic error, got warnings: $warnings") },
        )
}
