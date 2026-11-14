package org.nnezh.lltag

import org.junit.jupiter.api.Test
import org.nnezh.bred.context.ProgramGlobalContext
import org.nnezh.org.nnezh.ICGenerator.LLTACOperation
import org.nnezh.org.nnezh.ICGenerator.Operand
import org.nnezh.org.nnezh.compiler.TACCompilerImpl

class TACInstructionSequenceTest {
  private val types = ProgramGlobalContext()

  @Test
  fun `global static array emits global declaration and can be read`() {
    val tac = TACCompilerImpl().compile(
      """
      val values: Int[2] = [1, 2]

      fun main(): Unit {
          val first: Int = values[0]
      }
      """.trimIndent(),
    )

    verifyIR(types,tac) {
      val values = expectGlobalArray(
        "values", types.intType, size = 2,
        initializers = listOf(Operand.IntConst(1), Operand.IntConst(2)),
      )
      expectFunc("main", types.unitType)
      val index = expectAssign(Operand.IntConst(0), types.intType)
      val loaded = expectLoad(values, index)
      expectNamedAssign("first", loaded)
      expectRet()
    }
  }

  @Test
  fun `global static array without initializer emits global declaration`() {
    val tac = TACCompilerImpl().compile(
      """
      val values: Int[2]

      fun main(): Unit {
          val item: Int = values[0]
      }
      """.trimIndent(),
    )

    verifyIR(types,tac) {
      val values = expectGlobalArray("values", types.intType, size = 2)
      expectFunc("main", types.unitType)
      val index = expectAssign(Operand.IntConst(0), types.intType)
      val loaded = expectLoad(values, index)
      expectNamedAssign("item", loaded)
      expectRet()
    }
  }

  @Test
  fun factorialRecursionTest() {
    val tac = TACCompilerImpl().compile(
      """
      fun factorialRecursion(n: Int): Int {
        if (n < 1) {
          return 1
        }
        return n * factorialRecursion(n - 1)
      }

      fun main() {
        val n: Int = readInt()
        println(intToString(factorialRecursion(n)))
      }
      """.trimIndent(),
    ).take(6)

    verifyIR(types,tac) {
        expectFunc("factorialRecursion", types.intType)
        val paramN = expectParam("n", types.intType)
        val value1 = expectAssign(Operand.IntConst(1), types.intType)
        val ltResult = expectBinOp(
            opcode = LLTACOperation.LLTAC_LT,
            destinationType = types.booleanType,
            destinationPrefix = "var",
            arg1 = paramN,
            arg2 = value1,
        )
        val lbl = expectJumpIfNot(
            condition = ltResult
        )

        val temp = 0


//      val values = expectGlobalArray("values", types.intType, size = 2)
//      expectFunc("main", types.unitType)
//      val index = expectAssign(Operand.IntConst(0), types.intType)
//      val loaded = expectLoad(values, index)
//      expectNamedAssign("item", loaded)
//      expectRet()
    }
  }

}
