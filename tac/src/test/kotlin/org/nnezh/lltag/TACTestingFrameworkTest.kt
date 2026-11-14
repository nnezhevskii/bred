package org.nnezh.lltag

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.nnezh.bred.context.ProgramGlobalContext
import org.nnezh.org.nnezh.ICGenerator.LLTACElement
import org.nnezh.org.nnezh.ICGenerator.LLTACFunc
import org.nnezh.org.nnezh.ICGenerator.LLTACLabel
import org.nnezh.org.nnezh.ICGenerator.LLTACOperation.LLTAC_ADD_I32
import org.nnezh.org.nnezh.ICGenerator.LLTACOperation.LLTAC_FIELD
import org.nnezh.org.nnezh.ICGenerator.LLTACOperation.LLTAC_LDX
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
    @ValueSource(booleans = [false, true])
    fun `loading the second summand must not overwrite the first`(useGenericMatcher: Boolean) {
        val arr = Operand.Variable("param_arr_7", arrayType)
        val i = Operand.Variable("local_i_8", intType)
        val j = Operand.Variable("local_j_9", intType)
        val code = listOf(
            LLTACElement.load(arr.name, "var16", i.name, arrayType, intType, intType),
            LLTACElement.load(arr.name, "var16", j.name, arrayType, intType, intType),
            LLTACElement.binOp(LLTAC_ADD_I32, "var17", intType, "var16", intType, "var16", intType),
        )
        val failure = assertThrows(AssertionError::class.java) {
            verifyIR(types, code) {
                val left = if (useGenericMatcher) {
                    val destination = Operand.Variable("var16", intType)
                    expectInstruction(LLTAC_LDX, destination, arr, i)
                    destination
                } else {
                    expectLoad(arr, i)
                }
                val right = expectLoad(arr, j)
                expectBinOp(LLTAC_ADD_I32, "var", intType, left, right)
            }
        }
        assertTrue(failure.message.orEmpty().contains("TAC[1]"))
        assertTrue(failure.message.orEmpty().contains("var16"))
    }

    @Test
    fun `field metadata does not reserve a temporary of the same name`() {
        val code = listOf(LLTACElement.field("var16", intType), LLTACElement.assign("var16", intType, 1L))
        verifyIR(types, code) {
            expectInstruction(LLTAC_FIELD, Operand.Variable("var16", intType))
            expectAssign(Operand.IntConst(1), intType)
        }
    }

    @Test
    fun `unconditional jump must resolve in the final function`() {
        val code = listOf(LLTACElement.function("main", types.unitType), LLTACElement.jump(LLTACLabel("lbl42")))
        val failure = assertThrows(AssertionError::class.java) {
            verifyIR(types, code) {
                expectFunc("main", types.unitType)
                expectJump()
            }
        }
        assertTrue(failure.message.orEmpty().contains("TAC[1]"))
        assertTrue(failure.message.orEmpty().contains("lbl42"))
    }

    @Test
    fun `conditional jump cannot resolve to a label in the next function`() {
        val condition = Operand.Variable("param_flag_3", types.booleanType)
        val target = LLTACLabel("lbl42")
        val code = listOf(
            LLTACElement.function("first", types.unitType),
            LLTACElement.getParam(condition.name, condition.type),
            LLTACElement.jumpIfNot(target, condition.name, condition.type),
            LLTACElement.function("second", types.unitType),
            target,
        )
        val failure = assertThrows(AssertionError::class.java) {
            verifyIR(types, code) {
                expectFunc("first", types.unitType)
                expectJumpIfNot(expectParam("flag", types.booleanType))
                expectFunc("second", types.unitType)
                expectLabel(target)
            }
        }
        assertTrue(failure.message.orEmpty().contains("TAC[2]"))
        assertTrue(failure.message.orEmpty().contains("lbl42"))
    }

    @Test
    fun `functions may reuse labels and temporary names for independent values`() {
        val target = LLTACLabel("lbl42")
        val names = listOf("first", "second")
        val code = names.flatMap { name ->
            listOf(
                LLTACElement.function(name, intType), LLTACElement.jump(target), target,
                LLTACElement.assign("var1", intType, 1L), LLTACElement.ret("var1", intType),
            )
        }
        verifyIR(types, code) {
            for (name in names) {
                expectFunc(name, intType)
                expectLabel(expectJump())
                expectRet(expectAssign(Operand.IntConst(1), intType))
            }
        }
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
