package org.nnezh.lltag

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.nnezh.bred.context.ProgramGlobalContext
import org.nnezh.org.nnezh.ICGenerator.LLTACElement
import org.nnezh.org.nnezh.ICGenerator.LLTACFunc
import org.nnezh.org.nnezh.ICGenerator.Operand

class TACTestingFrameworkTest {
    private val types = ProgramGlobalContext()
    private val intType = types.intType
    private val arrayType = types.arrayOf(intType)

    private fun arrayRead(arrayAtLoad: String): List<LLTACElement> = listOf(
        LLTACElement.globalArray("global_values_91", arrayType, 1, listOf(Operand.IntConst(42))),
        LLTACElement.function("first", intType),
        LLTACElement.assign("var34", intType, 0L),
        LLTACElement.load(arrayAtLoad, "var87", "var34", arrayType, intType, intType),
        LLTACElement.assignVariable("local_result_19", intType, "var87"),
        LLTACElement.ret("local_result_19", intType),
    )

    private fun verifyArrayRead(code: List<LLTACElement>) = verifyIR(types, code) {
        val values = expectGlobalArray("values", intType, size = 1, initializers = listOf(Operand.IntConst(42)))
        expectFunc("first", intType)
        val index = expectAssign(Operand.IntConst(0), intType)
        val loaded = expectLoad(values, index)
        val result = expectNamedAssign("result", loaded)
        expectRet(result)
    }

    @Test
    fun `captured operands connect declarations loads assignments and returns`() {
        verifyArrayRead(arrayRead("global_values_91"))
    }

    @Test
    fun `load from another declaration of the same name fails at the load`() {
        val failure = assertThrows(AssertionError::class.java) { verifyArrayRead(arrayRead("global_values_92")) }
        assertTrue(failure.message.orEmpty().contains("TAC[3]"))
        assertTrue(failure.message.orEmpty().contains("global_values_91"))
        assertTrue(failure.message.orEmpty().contains("global_values_92"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["local_values_91", "global_more_values_91", "global_values_91_extra"])
    fun `declaration requires exact global prefix base name and numeric suffix`(name: String) {
        val code = listOf(LLTACElement.globalArray(name, arrayType, 1, null))
        val failure = assertThrows(AssertionError::class.java) {
            verifyIR(types, code) { expectGlobalArray("values", intType, size = 1) }
        }
        assertTrue(failure.message.orEmpty().contains("global_values_<id>"))
    }

    @Test
    fun `missing and trailing instructions report the first divergent position`() {
        val code = listOf(LLTACElement.function("main", types.unitType))
        val missing = assertThrows(AssertionError::class.java) {
            verifyIR(types, code) {
                expectNext<LLTACFunc>()
                expectRet()
            }
        }
        assertTrue(missing.message.orEmpty().contains("TAC[1]"))

        val trailing = assertThrows(AssertionError::class.java) { verifyIR(types, code) {} }
        assertTrue(trailing.message.orEmpty().contains("TAC[0]"))
    }
}
