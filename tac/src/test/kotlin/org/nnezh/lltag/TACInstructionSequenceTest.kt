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
    )

    verifyIR(types, tac) {
      expectFunc("factorialRecursion", types.intType)
      val paramN = expectParam("n", types.intType)
      val threshold = expectAssign(Operand.IntConst(1), types.intType)
      val isBaseCase = expectBinOp(LLTACOperation.LLTAC_LT, "var", types.booleanType, paramN, threshold)
      val recursiveCase = expectJumpIfNot(isBaseCase)
      val baseResult = expectAssign(Operand.IntConst(1), types.intType)
      expectRet(baseResult)

      expectLabel(recursiveCase)
      val one = expectAssign(Operand.IntConst(1), types.intType)
      val previousN = expectBinOp(LLTACOperation.LLTAC_SUB, "var", types.intType, paramN, one)
      expectCallArgument(previousN)
      val previousFactorial = expectCall("factorialRecursion", types.intType, argumentCount = 1)
      val product = expectBinOp(LLTACOperation.LLTAC_MUL, "var", types.intType, paramN, previousFactorial)
      expectRet(product)

      expectFunc("main", types.unitType)
      val input = expectCall("readInt", types.intType, argumentCount = 0, localName = "n")
      expectCallArgument(input)
      val factorial = expectCall("factorialRecursion", types.intType, argumentCount = 1)
      expectCallArgument(factorial)
      val formatted = expectCall("intToString", types.stringType, argumentCount = 1)
      expectCallArgument(formatted)
      expectUnitCall("println", argumentCount = 1)
      expectRet()
    }
  }
}
