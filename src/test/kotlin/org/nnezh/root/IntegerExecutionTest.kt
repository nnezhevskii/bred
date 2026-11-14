package org.nnezh.root

import org.junit.jupiter.api.Test

class IntegerExecutionTest : NativeProgramTestSupport() {
    @Test
    fun `minimum Int literal retains its signed value`() {
        assertOutput("fun main(): Int { printInt(-2147483648) return 0 }", listOf("-2147483648"))
    }

    @Test
    fun `overflow in addition reports the operator position and exits with failure`() {
        assertOutput(
            """
            fun main(): Int {
                printInt(2147483647 + 1)
                return 0
            }
            """,
            listOf("Integer overflow at 2:25"),
            expectedExit = 1,
        )
    }

    @Test
    fun `user function and runtime checked addition can be called in the same program`() {
        assertOutput(
            """
            fun checked_add_i32(a: Int, b: Int): Int { return a - b }
            fun main(): Int {
                printInt(checked_add_i32(9, 2))
                printInt(9 + 2)
                return 0
            }
            """,
            listOf("7", "11"),
        )
    }
}
