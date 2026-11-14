package org.nnezh.lltag

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.nnezh.bred.context.ProgramGlobalContext
import org.nnezh.org.nnezh.ICGenerator.LLTACElement
import org.nnezh.org.nnezh.ICGenerator.LLTACFunc
import org.nnezh.org.nnezh.ICGenerator.LLTACOperation
import org.nnezh.org.nnezh.ICGenerator.LLTACOperation.*
import org.nnezh.org.nnezh.ICGenerator.Operand
import org.nnezh.org.nnezh.ICGenerator.Operand.Variable
import org.nnezh.org.nnezh.compiler.TACCompilerImpl

class TACInstructionSequenceTest {
  private val types = ProgramGlobalContext()
  private val int = types.intType
  private val string = types.stringType
  private val intArray = types.arrayOf(int)

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

  // Verify whole functions independently so a mismatch in one body doesn't mask the next.
  private fun fixture(name: String, bodies: Map<String, TACTestingFramework.() -> Unit>): List<DynamicTest> {
    val source = requireNotNull(javaClass.getResource("/$name.bred")).readText()
    val functions = mutableMapOf<String, MutableList<LLTACElement>>()
    var body: MutableList<LLTACElement>? = null
    for (element in TACCompilerImpl().compile(source)) {
      if (element is LLTACFunc) {
        body = mutableListOf()
        check(functions.put(element.name, body) == null) { "$name: duplicate function ${element.name}" }
      }
      checkNotNull(body) { "$name: unexpected top-level element $element" }.add(element)
    }
    assertEquals(bodies.keys, functions.keys, "$name function declarations")
    return bodies.map { (function, verify) ->
      dynamicTest("$name.bred / $function") { verifyIR(types, functions.getValue(function), verify) }
    }
  }

  private fun TACTestingFramework.forBody(counter: Variable, limit: Variable, body: TACTestingFramework.() -> Unit) {
    val head = expectLabel()
    val end = expectJumpIfNot(compare(LLTAC_LT, counter, limit))
    body()
    expectInstruction(LLTAC_ADD_I32, counter, counter, intLiteral(1))
    expectJump(head)
    expectLabel(end)
  }

  private fun TACTestingFramework.expectNegativeOneReturn() = expectRet(expectNeg(intLiteral(1)))

  @TestFactory
  fun whileCountdown() = fixture("whileCountdown", mapOf(
    "countdown" to {
      expectFunc("countdown", int)
      val n = expectNamedAssign("n", expectParam("start", int))
      val sum = intLiteral(0, "sum")
      val head = expectLabel()
      val end = expectJumpIfNot(compare(LLTAC_GT, n, intLiteral(0)))
      expectInstruction(LLTAC_ADD_I32, sum, sum, n)
      expectInstruction(LLTAC_SUB, n, n, intLiteral(1))
      expectJump(head)
      expectLabel(end)
      expectRet(sum)
    },
    "main" to {
      expectFunc("main", types.unitType)
      val result = valueCall("countdown", int, intLiteral(4), local = "result")
      printLine(valueCall("intToString", string, result))
      expectRet()
    },
  ))

  @TestFactory
  fun isPositive() = fixture("isPositive", mapOf(
    "isPositive" to {
      expectFunc("isPositive", string)
      val x = expectParam("x", int)
      val nonPositive = expectJumpIfNot(compare(LLTAC_GT, x, intLiteral(0)))
      expectRet(text("positive"))
      val outerEnd = expectJump()
      expectLabel(nonPositive)
      val zero = expectJumpIfNot(compare(LLTAC_LT, x, intLiteral(0)))
      expectRet(text("negative"))
      val innerEnd = expectJump()
      expectLabel(zero)
      expectRet(text("zero"))
      expectLabel(innerEnd)
      expectLabel(outerEnd)
      expectRet() // Synthetic, unreachable after the three String returns.
    },
    "main" to {
      expectFunc("main", types.unitType)
      val x = valueCall("readInt", int, local = "x")
      val prefix = text("number: ")
      val sign = valueCall("isPositive", string, x)
      printLine(expectBinOp(LLTAC_ADD, "var", string, prefix, sign))
      expectRet()
    },
  ))

  @TestFactory
  fun minMax() = fixture("minMax", mapOf(
    "minIndex" to { extremeIndex("minIndex", LLTAC_LT) },
    "maxIndex" to { extremeIndex("maxIndex", LLTAC_GT) },
    "main" to {
      expectFunc("main", types.unitType)
      val size = intLiteral(50, "arraySize")
      val arr = expectAlloc("arr", int, 1024)
      val i = intLiteral(0, "i")
      val limit = expectNamedAssign("\$_right_borderi", size)
      forBody(i, limit) {
        val index = expectAssign(i, int)
        val value = valueCall("random", int, expectNeg(intLiteral(1000)), intLiteral(1000))
        expectInstruction(LLTAC_STX, arr, index, value)
      }
      for (function in listOf("minIndex", "maxIndex")) {
        val index = valueCall(function, int, arr, size)
        printLine(valueCall("intToString", string, index))
      }
      expectRet()
    },
  ))

  private fun TACTestingFramework.extremeIndex(name: String, comparison: LLTACOperation) {
    expectFunc(name, int)
    val arr = expectParam("arr", intArray)
    val size = expectParam("size", int)
    val nonEmpty = expectJumpIfNot(compare(LLTAC_EQ, size, intLiteral(0)))
    expectNegativeOneReturn()
    expectLabel(nonEmpty)
    val result = intLiteral(0, "result")
    val i = intLiteral(1, "i")
    val limit = expectNamedAssign("\$_right_borderi", size)
    forBody(i, limit) {
      val unchanged = expectJumpIfNot(compare(comparison, expectLoad(arr, i), expectLoad(arr, result)))
      expectInstruction(LLTAC_ASSIGN, result, i)
      expectLabel(unchanged)
    }
    expectRet(result)
  }

  @TestFactory
  fun nestedArraySearch() = fixture("nestedArraySearch", mapOf(
    "findPairSum" to {
      expectFunc("findPairSum", int)
      val arr = expectParam("arr", intArray)
      val size = expectParam("size", int)
      val target = expectParam("target", int)
      val search = expectJumpIfNot(compare(LLTAC_LT, size, intLiteral(1)))
      expectNegativeOneReturn()
      expectLabel(search)
      val i = intLiteral(0, "i")
      val outerLimit = expectNamedAssign("\$_right_borderi", size)
      forBody(i, outerLimit) {
        val j = expectBinOp(LLTAC_ADD_I32, "local_j_", int, i, intLiteral(1))
        val innerLimit = expectNamedAssign("\$_right_borderj", size)
        forBody(j, innerLimit) {
          val sum = expectBinOp(LLTAC_ADD_I32, "local_sum_", int, expectLoad(arr, i), expectLoad(arr, j))
          val noMatch = expectJumpIfNot(compare(LLTAC_EQ, sum, target))
          expectRet(i)
          expectLabel(noMatch)
        }
      }
      expectNegativeOneReturn()
    },
    "main" to {
      expectFunc("main", types.unitType)
      val data = expectAlloc("data", int, 6)
      for ((index, value) in listOf(2, 7, 11, 15, 4, 9).withIndex()) {
        expectInstruction(LLTAC_STX, data, intLiteral(index), intLiteral(value))
      }
      val index = valueCall("findPairSum", int, data, intLiteral(6), intLiteral(18), local = "idx")
      val found = expectJumpIfNot(compare(LLTAC_EQ, index, expectNeg(intLiteral(1))))
      printLine(text("not found"))
      val end = expectJump()
      expectLabel(found)
      printLine(valueCall("intToString", string, index))
      expectLabel(end)
      expectRet()
    },
  ))

  @TestFactory
  fun arrayBubble() = fixture("arrayBubble", mapOf(
    "bubbleSort" to {
      expectFunc("bubbleSort", types.unitType)
      val a = expectParam("a", intArray)
      val size = expectParam("size", int)
      val i = intLiteral(0, "i")
      val outerLimit = expectNamedAssign("\$_right_borderi", size)
      forBody(i, outerLimit) {
        val j = intLiteral(0, "j")
        val innerLimit = expectNamedAssign("\$_right_borderj", size)
        forBody(j, innerLimit) {
          val skipSwap = expectJumpIfNot(compare(LLTAC_LT, expectLoad(a, i), expectLoad(a, j)))
          val saved = expectNamedAssign("temp", expectLoad(a, j))
          val writeJ = expectAssign(j, int)
          expectInstruction(LLTAC_STX, a, writeJ, expectLoad(a, i))
          val writeI = expectAssign(i, int)
          expectInstruction(LLTAC_STX, a, writeI, saved)
          expectLabel(skipSwap)
        }
      }
      expectRet()
    },
    "main" to {
      expectFunc("main", types.unitType)
      val size = valueCall("readInt", int, local = "arrSize")
      val negative = compare(LLTAC_LT, size, intLiteral(0))
      val tooLarge = compare(LLTAC_GT, size, intLiteral(1024))
      val validSize = expectJumpIfNot(compare(LLTAC_OR, negative, tooLarge))
      expectRet()
      expectLabel(validSize)
      val arr = expectAlloc("arr", int, 1024)
      val i = intLiteral(0, "i")
      val limit = expectNamedAssign("\$_right_borderi", size)
      forBody(i, limit) {
        val index = expectAssign(i, int)
        val value = valueCall("random", int, intLiteral(0), intLiteral(100))
        expectInstruction(LLTAC_STX, arr, index, value)
      }
      printLine(valueCall(arrayToString, string, arr, size))
      expectCallArgument(arr)
      expectCallArgument(size)
      expectUnitCall("bubbleSort", 2)
      printLine(text("After sorting:"))
      printLine(valueCall(arrayToString, string, arr, size))
      // The call has no destination, but readInt still has its declared return type.
      expectDiscardedCall("readInt", int, 0)
      expectRet()
    },
    "toString_Int_String" to {
      expectFunc("toString_Int_String", string)
      val value = expectParam("value", int)
      expectRet(valueCall("intToString", string, value))
    },
    arrayToString to {
      expectFunc(arrayToString, string)
      val arr = expectParam("arr", intArray)
      val size = expectParam("size", int)
      val buff = text("", "buff")
      val nonEmpty = expectJumpIfNot(compare(LLTAC_EQ, size, intLiteral(0)))
      expectRet(text(""))
      expectLabel(nonEmpty)
      val multiple = expectJumpIfNot(compare(LLTAC_EQ, size, intLiteral(1)))
      expectRet(valueCall("toString_Int_String", string, expectLoad(arr, intLiteral(0))))
      expectLabel(multiple)
      val i = intLiteral(0, "i")
      val lastIndex = expectBinOp(LLTAC_SUB, "local_\$_right_borderi_", int, size, intLiteral(1))
      forBody(i, lastIndex) {
        val element = valueCall("toString_Int_String", string, expectLoad(arr, i))
        val joined = expectBinOp(LLTAC_ADD, "var", string, buff, element)
        expectInstruction(LLTAC_ADD, buff, joined, text(" "))
      }
      val last = expectBinOp(LLTAC_SUB, "var", int, size, intLiteral(1))
      val tail = valueCall("toString_Int_String", string, expectLoad(arr, last))
      expectInstruction(LLTAC_ADD, buff, buff, tail)
      expectRet(buff)
    },
  ))

  private val arrayToString = "arrayToString_Array_Int_Int_String"
}
