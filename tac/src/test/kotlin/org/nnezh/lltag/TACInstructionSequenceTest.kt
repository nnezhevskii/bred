package org.nnezh.lltag

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.nnezh.bred.common.TypeSign
import org.nnezh.bred.context.ProgramGlobalContext
import org.nnezh.org.nnezh.ICGenerator.LLTACElement
import org.nnezh.org.nnezh.ICGenerator.LLTACFunc
import org.nnezh.org.nnezh.ICGenerator.LLTACInstruction
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
  private val bool = types.booleanType
  private val unit = types.unitType

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
    val tac = compileFixture("factorial")

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

  private fun compileFixture(name: String): List<LLTACElement> {
    val source = requireNotNull(javaClass.getResource("/$name.bred")).readText()
    return TACCompilerImpl().compile(source)
  }

  // Each body has its own cursor so failures in one function do not hide the others.
  private fun fixture(
    name: String,
    bodies: Map<String, TACTestingFramework.() -> Unit>,
    declarations: ((List<LLTACElement>) -> Unit)? = null,
  ): List<DynamicTest> {
    val functions = mutableMapOf<String, MutableList<LLTACElement>>()
    val preamble = mutableListOf<LLTACElement>()
    var body = preamble
    for (element in compileFixture(name)) {
      if (element is LLTACFunc) {
        body = mutableListOf()
        check(functions.put(element.name, body) == null) { "$name: duplicate function ${element.name}" }
      }
      body.add(element)
    }
    assertEquals(bodies.keys, functions.keys, "$name function declarations")
    val declarationTests = if (declarations == null) {
      verifyIR(types, preamble) {}
      emptyList()
    } else {
      listOf(dynamicTest("$name.bred / declarations") { declarations(preamble) })
    }
    return declarationTests + bodies.map { (function, verify) ->
      dynamicTest("$name.bred / $function") { verifyIR(types, functions.getValue(function), verify) }
    }
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
    intToStringInstance to { printableInt() },
    arrayToString to { spaceSeparatedArray() },
  ))

  @TestFactory
  fun forRange() = fixture("for", mapOf(
    "main" to {
      expectFunc("main", unit)
      val sum = intLiteral(0, "sum")
      val i = intLiteral(1, "i")
      forBody(i, intLiteral(5, "\$_right_borderi")) {
        expectInstruction(LLTAC_ADD_I32, sum, sum, i)
      }
      printLine(decimal(sum))
      expectRet()
    },
  ))

  @TestFactory
  fun sortThree() = fixture("sortThree", mapOf(
    "sortThree" to {
      expectFunc("sortThree", unit)
      val a = expectParam("a", int)
      val b = expectParam("b", int)
      val c = expectParam("c", int)
      val x = expectNamedAssign("x", a)
      val y = expectNamedAssign("y", b)
      val z = expectNamedAssign("z", c)
      val temp = intLiteral(0, "temp")
      fun swapIfGreater(left: Variable, right: Variable) {
        expectIf(compare(LLTAC_GT, left, right)) {
          expectInstruction(LLTAC_ASSIGN, temp, left)
          expectInstruction(LLTAC_ASSIGN, left, right)
          expectInstruction(LLTAC_ASSIGN, right, temp)
        }
      }
      swapIfGreater(x, y)
      swapIfGreater(y, z)
      swapIfGreater(x, y)
      val first = join(decimal(x), text(" "))
      val second = join(join(first, decimal(y)), text(" "))
      printLine(join(second, decimal(z)))
      expectRet()
    },
    "main" to {
      expectFunc("main", unit)
      unitCall("sortThree", intLiteral(3), intLiteral(1), intLiteral(2))
      expectRet()
    },
  ))

  @TestFactory
  fun isEven() = fixture("isEven", mapOf(
    "isEven" to { parity("isEven", 1, "isOdd") },
    "isOdd" to { parity("isOdd", 0, "isEven") },
    "main" to {
      expectFunc("main", unit)
      val num = intLiteral(5, "num")
      val even = valueCall("isEven", int, num)
      expectIf(compare(LLTAC_EQ, even, intLiteral(1)), { printLine(text("Even")) }, { printLine(text("Odd")) })
      expectRet()
    },
  ))

  private fun TACTestingFramework.parity(name: String, base: Int, recurse: String) {
    expectFunc(name, int)
    val n = expectParam("n", int)
    expectIf(compare(LLTAC_EQ, n, intLiteral(0))) { expectRet(intLiteral(base)) }
    expectRet(valueCall(recurse, int, arithmetic(LLTAC_SUB, n, intLiteral(1))))
  }

  @TestFactory
  fun minValue() = fixture("minValue", mapOf(
    "minIndex" to {
      expectFunc("minIndex", int)
      val arr = expectParam("arr", intArray)
      val size = expectParam("size", int)
      expectIf(compare(LLTAC_LE, size, intLiteral(0))) { expectNegativeOneReturn() }
      val min = intLiteral(0, "min")
      forIndex(size, start = 1) { i ->
        expectIf(compare(LLTAC_LT, expectLoad(arr, i), expectLoad(arr, min))) {
          expectInstruction(LLTAC_ASSIGN, min, i)
        }
      }
      expectRet(min)
    },
    "main" to {
      expectFunc("main", unit)
      val arr = expectAlloc("arr", int, 100)
      val size = valueCall("readInt", int, local = "size")
      expectIf(compare(LLTAC_GT, size, intLiteral(100))) {
        printLine(text("technical restrictions"))
        expectRet()
      }
      forIndex(size) { i ->
        val index = expectAssign(i, int)
        expectInstruction(LLTAC_STX, arr, index, valueCall("random", int, intLiteral(0), intLiteral(100)))
      }
      val min = valueCall("minIndex", int, arr, size, local = "minInd")
      expectIf(compare(LLTAC_EQ, min, expectNeg(intLiteral(1))), {
        printLine(text("array is empty"))
      }, {
        printLine(join(text("min element is "), decimal(expectLoad(arr, min))))
      })
      expectRet()
    },
  ))

  @TestFactory
  fun conditionalAssignment() = fixture("random_case_failed_for_no_reason", mapOf(
    "someUsefulMethod" to {
      expectFunc("someUsefulMethod", int)
      expectParam("a", int)
      val b = expectParam("b", bool)
      val value = intLiteral(0, "value")
      expectIf(b, { expectInstruction(LLTAC_ASSIGN, value, Operand.IntConst(1)) }, {
        expectInstruction(LLTAC_ASSIGN, value, Operand.IntConst(2))
      })
      expectRet(value)
    },
    "main" to {
      expectFunc("main", int)
      expectParam("arg", types.arrayOf(string))
      expectParam("size", int)
      expectRet(intLiteral(0))
    },
  ))

  @TestFactory
  fun zeroTripFor() = fixture("zeroTripFor", mapOf(
    "sumRange" to {
      expectFunc("sumRange", int)
      val arr = expectParam("arr", intArray)
      val start = expectParam("start", int)
      val end = expectParam("end", int)
      val total = intLiteral(0, "total")
      val i = expectNamedAssign("i", start)
      forBody(i, expectNamedAssign("\$_right_borderi", end)) {
        expectInstruction(LLTAC_ADD_I32, total, total, expectLoad(arr, i))
      }
      expectRet(total)
    },
    "main" to {
      expectFunc("main", unit)
      val arr = intArrayWithValues("arr", 10, 20, 30, 40)
      val zero = valueCall("sumRange", int, arr, intLiteral(5), intLiteral(1), local = "zeroTrip")
      val single = valueCall("sumRange", int, arr, intLiteral(2), intLiteral(3), local = "single")
      printLine(join(join(decimal(zero), text(":")), decimal(single)))
      expectRet()
    },
  ))

  @TestFactory
  fun stringifyRow() = fixture("stringifyRow", mapOf(
    "appendInt" to {
      expectFunc("appendInt", string)
      val buf = expectParam("buf", string)
      expectRet(join(buf, decimal(expectParam("value", int))))
    },
    "formatRow" to {
      expectFunc("formatRow", unit)
      val arr = expectParam("arr", intArray)
      val size = expectParam("size", int)
      expectIf(compare(LLTAC_LT, size, intLiteral(0))) {
        printLine(text("bad size"))
        expectRet()
      }
      val line = text("", "line")
      forIndex(size) { i ->
        expectIf(compare(LLTAC_GT, i, intLiteral(0))) { expectInstruction(LLTAC_ADD, line, line, text("|")) }
        val element = expectLoad(arr, i)
        expectCallArgument(line)
        expectCallArgument(element)
        expectInstruction(LLTAC_CALL, line, Operand.FunctionCall("appendInt", string), Operand.IntConst(2))
      }
      printLine(line)
      expectRet()
    },
    "main" to {
      expectFunc("main", unit)
      val row = intArrayWithValues("row", 1, 2, 3)
      unitCall("formatRow", row, intLiteral(3))
      expectRet()
    },
  ))

  @TestFactory
  fun nestedControl() = fixture("nestedControl", mapOf(
    "classify" to {
      expectFunc("classify", int)
      val n = expectParam("n", int)
      val limit = expectParam("limit", int)
      val score = intLiteral(0, "score")
      val i = intLiteral(0, "i")
      expectWhile({ compare(LLTAC_LT, i, limit) }) {
        val inner = expectNamedAssign("inner", limit)
        expectWhile({ compare(LLTAC_GT, inner, intLiteral(0)) }) {
          expectIf(compare(LLTAC_GT, n, inner), {
            expectIf(compare(LLTAC_EQ, i, intLiteral(0)), {
              expectInstruction(LLTAC_ADD_I32, score, score, intLiteral(10))
            }, { increment(score) })
          }, {
            expectIf(compare(LLTAC_EQ, inner, intLiteral(1))) {
              expectInstruction(LLTAC_SUB, score, score, intLiteral(1))
            }
          })
          expectInstruction(LLTAC_SUB, inner, inner, intLiteral(1))
        }
        expectIf(compare(LLTAC_GT, score, intLiteral(100))) { expectRet(score) }
        increment(i)
      }
      expectRet(score)
    },
    "main" to {
      expectFunc("main", unit)
      val result = valueCall("classify", int, intLiteral(7), intLiteral(3), local = "result")
      printLine(decimal(result))
      expectRet()
    },
  ))

  @TestFactory
  fun arrayRotate() = fixture("arrayRotate", mapOf(
    "rotateLeft" to {
      expectFunc("rotateLeft", unit)
      val arr = expectParam("arr", intArray)
      val size = expectParam("size", int)
      expectIf(compare(LLTAC_LE, size, intLiteral(0))) { expectRet() }
      val first = expectNamedAssign("first", expectLoad(arr, intLiteral(0)))
      val i = intLiteral(0, "i")
      val end = arithmetic(LLTAC_SUB, size, intLiteral(1), local = "\$_right_borderi")
      forBody(i, end) {
        val next = arithmetic(LLTAC_ADD_I32, i, intLiteral(1), local = "next")
        expectInstruction(LLTAC_STX, arr, expectAssign(i, int), expectLoad(arr, next))
      }
      expectInstruction(LLTAC_STX, arr, arithmetic(LLTAC_SUB, size, intLiteral(1)), first)
      expectRet()
    },
    "main" to {
      expectFunc("main", unit)
      val buf = intArrayWithValues("buf", 10, 20, 30, 40)
      unitCall("rotateLeft", buf, intLiteral(4))
      val prefix = join(decimal(expectLoad(buf, intLiteral(0))), text("-"))
      printLine(join(prefix, decimal(expectLoad(buf, intLiteral(3)))))
      expectRet()
    },
  ))

  @TestFactory
  fun earlyReturnArray() = fixture("earlyReturnArray", mapOf(
    "firstNegative" to {
      expectFunc("firstNegative", int)
      val arr = expectParam("arr", intArray)
      val size = expectParam("size", int)
      expectIf(compare(LLTAC_LT, size, intLiteral(0))) { expectRet(expectNeg(intLiteral(2))) }
      expectIf(compare(LLTAC_EQ, size, intLiteral(0))) { expectNegativeOneReturn() }
      forIndex(size) { i ->
        expectIf(compare(LLTAC_LT, expectLoad(arr, i), intLiteral(0))) { expectRet(i) }
      }
      expectNegativeOneReturn()
    },
    "main" to {
      expectFunc("main", unit)
      val data = intArrayWithValues("data", 1, -5, 3)
      val idx = valueCall("firstNegative", int, data, intLiteral(3), local = "idx")
      expectIf(compare(LLTAC_LT, idx, intLiteral(0)), { printLine(text("none")) }, { printLine(decimal(idx)) })
      expectRet()
    },
  ))

  @TestFactory
  fun whileEarlyReturn() = fixture("whileEarlyReturn", mapOf(
    "findFirstNegative" to {
      expectFunc("findFirstNegative", int)
      val arr = expectParam("arr", intArray)
      val size = expectParam("size", int)
      val i = intLiteral(0, "i")
      expectWhile({ compare(LLTAC_LT, i, size) }) {
        expectIf(compare(LLTAC_LT, expectLoad(arr, i), intLiteral(0))) { expectRet(expectLoad(arr, i)) }
        increment(i)
      }
      expectRet(intLiteral(0))
    },
    "main" to {
      expectFunc("main", unit)
      val data = intArrayWithValues("data", 3, 7, -5, 1)
      val hit = valueCall("findFirstNegative", int, data, intLiteral(4), local = "hit")
      printLine(decimal(hit))
      expectRet()
    },
  ))

  @TestFactory
  fun whileZeroTrip() = fixture("whileZeroTrip", mapOf(
    "neverRuns" to {
      expectFunc("neverRuns", int)
      val marker = intLiteral(99, "marker")
      expectWhile({ boolLiteral(false) }) { expectInstruction(LLTAC_ASSIGN, marker, Operand.IntConst(0)) }
      expectRet(marker)
    },
    "conditionFalseFromStart" to {
      expectFunc("conditionFalseFromStart", int)
      val bound = expectParam("bound", int)
      val acc = intLiteral(0, "acc")
      expectWhile({ compare(LLTAC_GT, bound, intLiteral(100)) }) { increment(acc) }
      expectRet(acc)
    },
    "main" to {
      expectFunc("main", unit)
      val a = valueCall("neverRuns", int, local = "a")
      val b = valueCall("conditionFalseFromStart", int, intLiteral(5), local = "b")
      printLine(join(join(decimal(a), text(":")), decimal(b)))
      expectRet()
    },
  ))

  @TestFactory
  fun whileGuardedAccum() = fixture("whileGuardedAccum", mapOf(
    "boundedSum" to {
      expectFunc("boundedSum", int)
      val limit = expectParam("limit", int)
      val cap = expectParam("cap", int)
      val i = intLiteral(0, "i")
      val total = intLiteral(0, "total")
      expectWhile({ compare(LLTAC_AND, compare(LLTAC_LT, i, limit), compare(LLTAC_LT, total, cap)) }) {
        expectInstruction(LLTAC_ADD_I32, total, total, i)
        increment(i)
      }
      expectRet(total)
    },
    "main" to {
      expectFunc("main", unit)
      val small = valueCall("boundedSum", int, intLiteral(10), intLiteral(50), local = "small")
      val clipped = valueCall("boundedSum", int, intLiteral(100), intLiteral(15), local = "clipped")
      printLine(join(join(decimal(small), text(",")), decimal(clipped)))
      expectRet()
    },
  ))

  private val binarySearchInt = "binarySearch_Array_Int_Int_Int_Int"
  private val intEquals = "equals_Int_Int_Boolean"
  private val intLess = "less_Int_Int_Boolean"

  private fun TACTestingFramework.intComparison(name: String, op: LLTACOperation) {
    expectFunc(name, bool)
    val left = expectParam("a", int)
    val right = expectParam("b", int)
    expectRet(compare(op, left, right))
  }

  // Both programs specialize the same search with different source names for two variables.
  private fun TACTestingFramework.search(targetName: String, middleName: String) {
    expectFunc(binarySearchInt, int)
    val arr = expectParam("arr", intArray)
    val size = expectParam("size", int)
    val target = expectParam(targetName, int)
    expectIf(compare(LLTAC_EQ, size, intLiteral(0))) { expectNegativeOneReturn() }
    val left = intLiteral(0, "left")
    val right = arithmetic(LLTAC_SUB, size, intLiteral(1), local = "right")
    expectWhile({ compare(LLTAC_LE, left, right) }) {
      val middle = arithmetic(LLTAC_DIV, arithmetic(LLTAC_ADD_I32, left, right), intLiteral(2), local = middleName)
      expectIf(valueCall(intEquals, bool, expectLoad(arr, middle), target)) { expectRet(middle) }
      expectIf(valueCall(intLess, bool, target, expectLoad(arr, middle)), {
        expectInstruction(LLTAC_SUB, right, middle, intLiteral(1))
      }, {
        expectInstruction(LLTAC_ADD_I32, left, middle, intLiteral(1))
      })
    }
    expectNegativeOneReturn()
  }

  @TestFactory
  fun binarySearch() = fixture("binarySearch", mapOf(
    intToStringInstance to { printableInt() },
    intEquals to { intComparison(intEquals, LLTAC_EQ) },
    intLess to { intComparison(intLess, LLTAC_LT) },
    "greater_Int_Int_Boolean" to { intComparison("greater_Int_Int_Boolean", LLTAC_GT) },
    arrayToString to { spaceSeparatedArray() },
    binarySearchInt to { search("element", "mid") },
    "main" to {
      expectFunc("main", unit)
      val size = valueCall("readInt", int, local = "arrSize")
      val negative = compare(LLTAC_LT, size, intLiteral(0))
      val tooLarge = compare(LLTAC_GT, size, intLiteral(1024))
      expectIf(compare(LLTAC_OR, negative, tooLarge)) { expectRet() }
      val arr = expectAlloc("arr", int, 1024)
      forIndex(size) { i ->
        expectInstruction(LLTAC_STX, arr, expectAssign(i, int), arithmetic(LLTAC_MUL, i, intLiteral(2)))
      }
      expectIf(compare(LLTAC_GT, size, intLiteral(0))) {
        val destination = arithmetic(LLTAC_SUB, size, intLiteral(1))
        val last = expectLoad(arr, arithmetic(LLTAC_SUB, size, intLiteral(1)))
        expectInstruction(LLTAC_STX, arr, destination, arithmetic(LLTAC_MUL, last, intLiteral(11)))
      }
      printLine(valueCall(arrayToString, string, arr, size))
      val element = valueCall("readInt", int, local = "elementToSeek")
      printLine(join(text("index = "), decimal(valueCall(binarySearchInt, int, arr, size, element))))
      expectDiscardedCall("readInt", int, 0)
      expectRet()
    },
  ))

  private val bubbleSortInt = "bubbleSort_Array_Int_Int_Unit"
  private val isSortedInt = "isSorted_Array_Int_Int_Boolean"
  private val printValueInt = "printValue_Int_Unit"

  @TestFactory
  fun complexShock() = fixture("complexShock", mapOf(
    intToStringInstance to { printableInt() },
    intEquals to { intComparison(intEquals, LLTAC_EQ) },
    intLess to { intComparison(intLess, LLTAC_LT) },
    printValueInt to {
      expectFunc(printValueInt, unit)
      printLine(valueCall(intToStringInstance, string, expectParam("value", int)))
      expectRet()
    },
    arrayToString to {
      expectFunc(arrayToString, string)
      val arr = expectParam("arr", intArray)
      val size = expectParam("size", int)
      val buffer = text("", "text")
      forIndex(size) { i ->
        expectIf(compare(LLTAC_EQ, i, intLiteral(0)), {
          expectInstruction(LLTAC_ADD, buffer, buffer, valueCall(intToStringInstance, string, expectLoad(arr, i)))
        }, {
          val prefix = join(buffer, text(", "))
          expectInstruction(LLTAC_ADD, buffer, prefix, valueCall(intToStringInstance, string, expectLoad(arr, i)))
        })
      }
      expectRet(buffer)
    },
    isSortedInt to {
      expectFunc(isSortedInt, bool)
      val arr = expectParam("arr", intArray)
      val size = expectParam("size", int)
      val i = intLiteral(1, "i")
      expectWhile({ compare(LLTAC_LT, i, size) }) {
        val current = expectLoad(arr, i)
        val previous = expectLoad(arr, arithmetic(LLTAC_SUB, i, intLiteral(1)))
        expectIf(valueCall(intLess, bool, current, previous)) { expectRet(boolLiteral(false)) }
        increment(i)
      }
      expectRet(boolLiteral(true))
    },
    bubbleSortInt to {
      expectFunc(bubbleSortInt, unit)
      val arr = expectParam("arr", intArray)
      val size = expectParam("size", int)
      val pass = intLiteral(0, "pass")
      expectWhile({ compare(LLTAC_LT, pass, size) }) {
        val j = intLiteral(1, "j")
        fun previous() = arithmetic(LLTAC_SUB, j, intLiteral(1))
        expectWhile({ compare(LLTAC_LT, j, arithmetic(LLTAC_SUB, size, pass)) }) {
          expectIf(valueCall(intLess, bool, expectLoad(arr, j), expectLoad(arr, previous()))) {
            val tmp = expectNamedAssign("tmp", expectLoad(arr, j))
            expectInstruction(LLTAC_STX, arr, expectAssign(j, int), expectLoad(arr, previous()))
            expectInstruction(LLTAC_STX, arr, previous(), tmp)
          }
          increment(j)
        }
        increment(pass)
      }
      expectRet()
    },
    binarySearchInt to { search("target", "middle") },
    "clampSize" to {
      expectFunc("clampSize", int)
      val value = expectParam("value", int)
      val max = expectParam("maxValue", int)
      expectIf(compare(LLTAC_LT, value, intLiteral(0))) { expectRet(intLiteral(0)) }
      expectIf(compare(LLTAC_GT, value, max)) { expectRet(max) }
      expectRet(value)
    },
    "positiveSeed" to {
      expectFunc("positiveSeed", int)
      val seed = expectParam("seed", int)
      expectIf(compare(LLTAC_LT, seed, intLiteral(0))) { expectRet(expectNeg(seed)) }
      expectRet(seed)
    },
    "fillData" to {
      expectFunc("fillData", unit)
      val arr = expectParam("arr", intArray)
      val size = expectParam("size", int)
      val seed = expectParam("seed", int)
      val state = arithmetic(LLTAC_ADD_I32, valueCall("positiveSeed", int, seed), intLiteral(17), local = "state")
      forIndex(size) { i ->
        val shifted = arithmetic(LLTAC_ADD_I32, arithmetic(LLTAC_MUL, state, intLiteral(37)), intLiteral(23))
        expectInstruction(LLTAC_MOD, state, shifted, intLiteral(101))
        expectInstruction(LLTAC_STX, arr, expectAssign(i, int), arithmetic(LLTAC_SUB, state, intLiteral(50)))
      }
      expectRet()
    },
    "copyArray" to {
      expectFunc("copyArray", unit)
      val source = expectParam("source", intArray)
      val target = expectParam("target", intArray)
      val size = expectParam("size", int)
      forIndex(size) { i -> expectInstruction(LLTAC_STX, target, expectAssign(i, int), expectLoad(source, i)) }
      expectRet()
    },
    "sumArray" to {
      expectFunc("sumArray", int)
      val arr = expectParam("arr", intArray)
      val size = expectParam("size", int)
      val total = intLiteral(0, "total")
      forIndex(size) { i -> expectInstruction(LLTAC_ADD_I32, total, total, expectLoad(arr, i)) }
      expectRet(total)
    },
    "minArray" to { extremeValue("minArray", LLTAC_LT) },
    "maxArray" to { extremeValue("maxArray", LLTAC_GT) },
    "countLessThan" to { countThreshold("countLessThan", LLTAC_LT) },
    "countGreaterOrEqual" to { countThreshold("countGreaterOrEqual", LLTAC_GE) },
    "countEven" to {
      expectFunc("countEven", int)
      val arr = expectParam("arr", intArray)
      val size = expectParam("size", int)
      val count = intLiteral(0, "count")
      forIndex(size) { i ->
        val remainder = arithmetic(LLTAC_MOD, expectLoad(arr, i), intLiteral(2))
        expectIf(compare(LLTAC_EQ, remainder, intLiteral(0))) { increment(count) }
      }
      expectRet(count)
    },
    "checksum" to {
      expectFunc("checksum", int)
      val arr = expectParam("arr", intArray)
      val size = expectParam("size", int)
      val result = intLiteral(0, "result")
      forIndex(size) { i ->
        val weighted = arithmetic(LLTAC_MUL, expectLoad(arr, i), arithmetic(LLTAC_ADD_I32, i, intLiteral(1)))
        expectInstruction(LLTAC_ADD_I32, result, result, weighted)
      }
      expectRet(result)
    },
    "pad2" to {
      expectFunc("pad2", string)
      val value = expectParam("value", int)
      expectIf(compare(LLTAC_LT, value, intLiteral(10))) { expectRet(join(text("0"), decimal(value))) }
      expectRet(decimal(value))
    },
    "averageText" to {
      expectFunc("averageText", string)
      val sum = expectParam("sum", int)
      val size = expectParam("size", int)
      expectIf(compare(LLTAC_EQ, size, intLiteral(0))) { expectRet(text("n/a")) }
      val scaled = arithmetic(LLTAC_DIV, arithmetic(LLTAC_MUL, sum, intLiteral(100)), size, local = "scaled")
      val negative = boolLiteral(false, "negative")
      expectIf(compare(LLTAC_LT, scaled, intLiteral(0))) {
        expectInstruction(LLTAC_ASSIGN, negative, Operand.BoolConst(true))
        expectInstruction(LLTAC_NEG, scaled, scaled)
      }
      val whole = arithmetic(LLTAC_DIV, scaled, intLiteral(100), local = "whole")
      val fraction = arithmetic(LLTAC_MOD, scaled, intLiteral(100), local = "fraction")
      val prefix = join(decimal(whole), text("."))
      val result = arithmetic(LLTAC_ADD, prefix, valueCall("pad2", string, fraction), local = "text")
      expectIf(negative) { expectRet(join(text("-"), result)) }
      expectRet(result)
    },
    "qualityScore" to {
      expectFunc("qualityScore", types.doubleType)
      val sorted = expectParam("sorted", bool)
      val spread = expectParam("hasSpread", bool)
      val evens = expectParam("evenCount", int)
      val score = doubleLiteral(0.0, "score")
      expectIf(sorted) { expectInstruction(LLTAC_ADD, score, score, doubleLiteral(1.25)) }
      expectIf(spread) { expectInstruction(LLTAC_ADD, score, score, doubleLiteral(0.75)) }
      expectIf(compare(LLTAC_GT, evens, intLiteral(0))) { expectInstruction(LLTAC_ADD, score, score, doubleLiteral(0.50)) }
      expectRet(arithmetic(LLTAC_MUL, score, doubleLiteral(2.0)))
    },
    "printBoolean" to {
      expectFunc("printBoolean", unit)
      val label = expectParam("label", string)
      val value = expectParam("value", bool)
      expectIf(value, { printLine(join(label, text("true"))) }, { printLine(join(label, text("false"))) })
      expectRet()
    },
    "main" to { complexShockMain() },
  ), declarations = ::verifyComplexShockPreamble)

  private fun verifyComplexShockPreamble(preamble: List<LLTACElement>) {
    verifyIR(types, preamble) {}
  }

  @Test
  fun `complexShock rejects a file-scope assignment to ProgramName`() {
    val preamble = compileFixture("complexShock").takeWhile { it !is LLTACFunc }
    val assignment = LLTACElement.assign("global_ProgramName_42", string, "Bred data analyzer")
    val failure = assertThrows(AssertionError::class.java) { verifyComplexShockPreamble(preamble + assignment) }
    assertTrue(failure.message.orEmpty().contains("global_ProgramName_42"))
  }

  private fun TACTestingFramework.extremeValue(name: String, comparison: LLTACOperation) {
    expectFunc(name, int)
    val arr = expectParam("arr", intArray)
    val size = expectParam("size", int)
    expectIf(compare(LLTAC_EQ, size, intLiteral(0))) { expectRet(intLiteral(0)) }
    val result = expectNamedAssign("result", expectLoad(arr, intLiteral(0)))
    val i = intLiteral(1, "i")
    expectWhile({ compare(LLTAC_LT, i, size) }) {
      expectIf(compare(comparison, expectLoad(arr, i), result)) {
        expectInstruction(LLTAC_ASSIGN, result, expectLoad(arr, i))
      }
      increment(i)
    }
    expectRet(result)
  }

  private fun TACTestingFramework.countThreshold(name: String, comparison: LLTACOperation) {
    expectFunc(name, int)
    val arr = expectParam("arr", intArray)
    val size = expectParam("size", int)
    val threshold = expectParam("threshold", int)
    val count = intLiteral(0, "count")
    forIndex(size) { i -> expectIf(compare(comparison, expectLoad(arr, i), threshold)) { increment(count) } }
    expectRet(count)
  }

  private fun TACTestingFramework.complexShockMain() {
    expectFunc("main", unit)
    printLine(text("Bred data analyzer"))
    printLine(text("Enter array size, seed, value to search, threshold:"))
    val requested = valueCall("readInt", int, local = "requestedSize")
    val seed = valueCall("readInt", int, local = "seed")
    val target = valueCall("readInt", int, local = "target")
    val threshold = valueCall("readInt", int, local = "threshold")
    val size = valueCall("clampSize", int, requested, intLiteral(32), local = "arrSize")
    val raw = expectAlloc("raw", int, 64)
    val sorted = expectAlloc("sorted", int, 64)
    unitCall("fillData", raw, size, seed)
    unitCall("copyArray", raw, sorted, size)

    fun report(label: String, value: () -> Variable) = printLine(join(text(label), value()))
    report("normalized size = ") { decimal(size) }
    report("raw data = ") { valueCall(arrayToString, string, raw, size) }
    unitCall(bubbleSortInt, sorted, size)
    val total = valueCall("sumArray", int, sorted, size, local = "total")
    val min = valueCall("minArray", int, sorted, size, local = "minValue")
    val max = valueCall("maxArray", int, sorted, size, local = "maxValue")
    val below = valueCall("countLessThan", int, sorted, size, threshold, local = "below")
    val above = valueCall("countGreaterOrEqual", int, sorted, size, threshold, local = "aboveOrEqual")
    val even = valueCall("countEven", int, sorted, size, local = "evenCount")
    val found = valueCall(binarySearchInt, int, sorted, size, target, local = "foundIndex")
    val sortedOk = valueCall(isSortedInt, bool, sorted, size, local = "sortedOk")
    val spread = compare(LLTAC_GT, max, min, local = "hasSpread")
    val empty = compare(LLTAC_EQ, size, intLiteral(0))
    val inOrder = compare(LLTAC_AND, sortedOk, compare(LLTAC_LE, min, max))
    val healthy = compare(LLTAC_OR, empty, inOrder, local = "healthy")
    report("sorted data = ") { valueCall(arrayToString, string, sorted, size) }
    report("sum = ") { decimal(total) }
    report("min = ") { decimal(min) }
    report("max = ") { decimal(max) }
    report("average = ") { valueCall("averageText", string, total, size) }
    report("threshold = ") { decimal(threshold) }
    report("below threshold = ") { decimal(below) }
    report("above or equal threshold = ") { decimal(above) }
    report("even count = ") { decimal(even) }
    report("checksum = ") { decimal(valueCall("checksum", int, sorted, size)) }
    report("search target = ") { decimal(target) }
    report("binary search index = ") { decimal(found) }
    unitCall("printBoolean", text("sorted = "), sortedOk)
    unitCall("printBoolean", text("has spread = "), spread)
    unitCall("printBoolean", text("healthy report = "), healthy)
    val score = valueCall("qualityScore", types.doubleType, sortedOk, spread, even, local = "score")
    report("quality score = ") { valueCall("doubleToString", string, score) }
    printLine(text("generic print of checksum:"))
    unitCall(printValueInt, valueCall("checksum", int, sorted, size))
    expectDiscardedCall("readInt", int, 0)
    expectRet()
  }

  @TestFactory
  fun deploymentHealthRadar(): List<DynamicTest> {
    val health = types.addType("Health", false)
    val alert = types.addType("Alert", false)
    val maybe = types.addType("Maybe_Int", false)
    val serviceType = types.declareProductType("ServiceSnapshot", mapOf("name" to string, "score" to int, "health" to health))
    val box = types.declareProductType("ScoreBox_Int", mapOf("value" to int))
    val remember = "remember_Int_Maybe_Int"
    val boxScore = "boxScore_Int_ScoreBox_Int"

    return fixture("deploymentHealthRadar", mapOf(
      remember to {
        expectFunc(remember, maybe)
        expectRet(valueCall("Just", maybe, expectParam("value", int)))
      },
      boxScore to {
        expectFunc(boxScore, box)
        expectRet(valueCall("ScoreBox_Int", box, expectParam("value", int)))
      },
      "healthLabel" to {
        expectFunc("healthLabel", string)
        val subject = expectParam("health", health)
        val label = text("", "label")
        fun scoredVariant(prefix: String, field: String) {
          val score = expectField(subject, field, int, local = "score")
          val description = join(text(prefix), decimal(score))
          expectInstruction(LLTAC_ADD, label, description, text(")"))
        }
        expectWhen(subject,
          0 to { expectInstruction(LLTAC_ASSIGN, label, Operand.StringConst("HEALTHY")) },
          1 to { scoredVariant("DEGRADED(", "Degraded_value0") },
          2 to { scoredVariant("CRITICAL(", "Critical_score") },
          3 to { expectInstruction(LLTAC_ASSIGN, label, Operand.StringConst("OFFLINE")) },
        )
        expectRet(label)
      },
      "main" to {
        expectFunc("main", unit)
        val service = valueCall("ServiceSnapshot", serviceType, text("search-index"), intLiteral(64),
          valueCall("Degraded", health, intLiteral(64)), local = "service")
        val best = valueCall(boxScore, box, expectField(service, "score", int), local = "bestBox")
        val checkpoint = valueCall(remember, maybe, expectField(best, "value", int), local = "checkpoint")
        val notification = valueCall("Notify", alert, intLiteral(2),
          valueCall("healthLabel", string, expectField(service, "health", health)), local = "alert")
        expectWhen(checkpoint,
          0 to {
            val bestScore = expectField(checkpoint, "Just_value", int, local = "bestScore")
            printLine(join(text("best="), decimal(bestScore)))
          },
          1 to { printLine(text("empty")) },
        )
        expectWhen(notification,
          0 to { printLine(text("ship")) },
          1 to {
            val level = expectField(notification, "Notify_level", int, local = "level")
            val message = expectField(notification, "Notify_message", string, local = "message")
            printLine(join(text("level="), decimal(level)))
            printLine(message)
          },
        )
        expectRet()
      },
    )) { preamble ->
      verifyTypeDeclarations(preamble, mapOf(
        health to {
          expectSum(health, "Healthy" to emptyList(), "Degraded" to listOf(Variable("value0", int)),
            "Critical" to listOf(Variable("score", int)), "Offline" to emptyList())
        },
        alert to {
          expectSum(alert, "NoAlert" to emptyList(), "Notify" to listOf(Variable("level", int), Variable("message", string)))
        },
        serviceType to { expectStruct(serviceType, Variable("name", string), Variable("score", int), Variable("health", health)) },
        box to { expectStruct(box, Variable("value", int)) },
        maybe to { expectSum(maybe, "Just" to listOf(Variable("value", int)), "Nothing" to emptyList()) },
      ))
    }
  }

  // Unrelated types may be emitted in any order. Each declaration and its metadata are consumed in full.
  private fun verifyTypeDeclarations(elements: List<LLTACElement>, expected: Map<TypeSign, TACTestingFramework.() -> Unit>) {
    val declarations = mutableMapOf<TypeSign, MutableList<LLTACElement>>()
    var body: MutableList<LLTACElement>? = null
    for (element in elements) {
      if (element is LLTACInstruction && element.opcode == LLTAC_STRUCT_DEF) {
        val type = (element.destination as Operand.TypeConst).type
        body = mutableListOf()
        check(declarations.put(type, body) == null) { "Repeated declaration of ${type.render()}" }
      }
      checkNotNull(body) { "Expected a type declaration, found $element" }.add(element)
    }
    assertEquals(expected.keys, declarations.keys, "declared types")
    val defined = mutableSetOf<TypeSign>()
    for ((type, block) in declarations) {
      verifyIR(types, block, expected.getValue(type))
      for (field in block.filterIsInstance<LLTACInstruction>().filter { it.opcode == LLTAC_FIELD }) {
        val member = field.destination as Variable
        if (member.type in expected) {
          assertTrue(member.type in defined) {
            "${type.name}.${member.name} requires ${member.type.name} to be declared first"
          }
        }
      }
      defined.add(type)
    }
  }

  @Test
  fun `a by-value field requires its type first while independent types may move`() {
    val health = types.addType("Health", false)
    val service = types.declareProductType("ServiceSnapshot", mapOf("health" to health))
    val box = types.declareProductType("ScoreBox_Int", mapOf("value" to int))
    val healthBlock = listOf(LLTACElement.structDef(health), LLTACElement.field("tag", int), LLTACElement.endStructDef())
    val serviceBlock = listOf(LLTACElement.structDef(service), LLTACElement.field("health", health), LLTACElement.endStructDef())
    val boxBlock = listOf(LLTACElement.structDef(box), LLTACElement.field("value", int), LLTACElement.endStructDef())
    val expected = mapOf<TypeSign, TACTestingFramework.() -> Unit>(
      health to { expectStruct(health, Variable("tag", int)) },
      service to { expectStruct(service, Variable("health", health)) },
      box to { expectStruct(box, Variable("value", int)) },
    )
    verifyTypeDeclarations(boxBlock + healthBlock + serviceBlock, expected)
    verifyTypeDeclarations(healthBlock + serviceBlock + boxBlock, expected)
    val failure = assertThrows(AssertionError::class.java) {
      verifyTypeDeclarations(serviceBlock + healthBlock + boxBlock, expected)
    }
    assertTrue(failure.message.orEmpty().contains("ServiceSnapshot.health requires Health"))
  }

  private val intToStringInstance = "toString_Int_String"
  private val arrayToString = "arrayToString_Array_Int_Int_String"

  private fun TACTestingFramework.printableInt() {
    expectFunc(intToStringInstance, string)
    expectRet(decimal(expectParam("value", int)))
  }

  private fun TACTestingFramework.spaceSeparatedArray() {
    expectFunc(arrayToString, string)
    val arr = expectParam("arr", intArray)
    val size = expectParam("size", int)
    val buff = text("", "buff")
    expectIf(compare(LLTAC_EQ, size, intLiteral(0))) { expectRet(text("")) }
    expectIf(compare(LLTAC_EQ, size, intLiteral(1))) {
      expectRet(valueCall(intToStringInstance, string, expectLoad(arr, intLiteral(0))))
    }
    val i = intLiteral(0, "i")
    val lastIndex = arithmetic(LLTAC_SUB, size, intLiteral(1), local = "\$_right_borderi")
    forBody(i, lastIndex) {
      val element = valueCall(intToStringInstance, string, expectLoad(arr, i))
      val joined = join(buff, element)
      expectInstruction(LLTAC_ADD, buff, joined, text(" "))
    }
    val last = arithmetic(LLTAC_SUB, size, intLiteral(1))
    val tail = valueCall(intToStringInstance, string, expectLoad(arr, last))
    expectInstruction(LLTAC_ADD, buff, buff, tail)
    expectRet(buff)
  }
}
