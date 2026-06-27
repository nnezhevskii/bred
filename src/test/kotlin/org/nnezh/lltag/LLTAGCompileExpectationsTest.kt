package org.nnezh.lltag

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.nnezh.org.nnezh.compiler.TACCompilerImpl

/**
 * Contract tests for the TAC compilation pipeline.
 *
 * - [assertParseFails] / [assertSemanticRejects] — expected failures.
 * - [assertCompiles] — must reach 3AC generation.
 */
class LLTAGCompileExpectationsTest {

  private fun assertParseFails(src: String) {
    val ex = assertThrows(IllegalStateException::class.java) { TACCompilerImpl().compile(src) }
    assertTrue(
      ex.message?.contains("parse error", ignoreCase = true) == true,
      "expected parse error, got: ${ex.message}",
    )
  }

  private fun assertSemanticRejects(src: String) {
    val ex = assertThrows(IllegalStateException::class.java) { TACCompilerImpl().compile(src) }
    assertTrue(
      ex.message?.contains("semantic analysis failed", ignoreCase = true) == true,
      "expected semantic rejection, got: ${ex.message}",
    )
  }

  private fun assertCompiles(src: String) {
    val tac = TACCompilerImpl().compile(src)
    assertTrue(tac.isNotEmpty(), "expected non-empty 3AC output")
  }

  // region Expected failures — language / semantic rules

  @Test
  fun `sumRange with to parameter and for range is a parse error`() {
    assertParseFails(
      """
      fun sumRange(arr: Int[], from: Int, to: Int): Int {
          var total: Int = 0
          for (i in from to to) {
              total = total + arr[i]
          }
          return total
      }
      fun main(): Unit { }
      """.trimIndent(),
    )
  }

  @Test
  fun `local variable named to is a parse error`() {
    assertParseFails(
      """
      fun main(): Unit {
          var to: Int = 5
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `parameter named to is a parse error`() {
    assertParseFails(
      """
      fun span(from: Int, to: Int): Int {
          return to - from
      }
      fun main(): Unit { }
      """.trimIndent(),
    )
  }

  @Test
  fun `for range endpoint named to is a parse error`() {
    assertParseFails(
      """
      fun main(): Unit {
          var end: Int = 5
          for (i in 1 to to) {
              println("loop")
          }
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `too many array initializer elements is a semantic rejection`() {
    assertSemanticRejects(
      """
      fun main(): Unit {
          val arr: Int[2] = [1, 2, 3]
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `inconsistent array initializer element types is a semantic rejection`() {
    assertSemanticRejects(
      """
      fun main(): Unit {
          val arr: Int[2] = [1, true]
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `scalar used as array in assignment is a semantic rejection`() {
    assertSemanticRejects(
      """
      fun main(): Unit {
          val x: Int = 0
          x[0] = 1
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `non-integer array index is a semantic rejection`() {
    assertSemanticRejects(
      """
      fun main(): Unit {
          val arr: Int[2] = [1, 2]
          val x: Int = arr[true]
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `wrong arity call is a semantic rejection`() {
    assertSemanticRejects(
      """
      fun foo(a: Int, b: Int): Unit { }
      fun main(): Unit {
          foo(1)
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `function name passed where int expected is a semantic rejection`() {
    assertSemanticRejects(
      """
      fun callback(): Unit { }
      fun invoke(x: Int): Unit { }
      fun main(): Unit {
          invoke(callback)
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `forward reference in initializer is a semantic rejection`() {
    assertSemanticRejects(
      """
      fun add(a: Int, b: Int): Int {
          return a
      }
      fun main(): Unit {
          val sum: Int = add(1, tail)
          val tail: Int = 2
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `self reference in initializer is a semantic rejection`() {
    assertSemanticRejects(
      """
      fun main(): Unit {
          val a: Int = a + 1
      }
      """.trimIndent(),
    )
  }

  // endregion

  // region Expected success — must compile to 3AC

  @Test
  fun `for range with end parameter compiles`() {
    assertCompiles(
      """
      fun sumRange(arr: Int[], from: Int, end: Int): Int {
          var total: Int = 0
          for (i in from to end) {
              total = total + arr[i]
          }
          return total
      }
      fun main(): Unit {
          val data: Int[3]
          data[0] = 1
          data[1] = 2
          data[2] = 3
          var s: Int = sumRange(data, 0, 2)
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `zero-sized array without initializer compiles`() {
    assertCompiles(
      """
      fun main(): Unit {
          val arr: Int[0]
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `zero-trip for loop compiles`() {
    assertCompiles(
      """
      fun main(): Unit {
          for (i in 5 to 1) {
              println("never")
          }
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `nested for over array compiles`() {
    assertCompiles(
      """
      fun main(): Unit {
          val arr: Int[4]
          arr[0] = 1
          arr[1] = 2
          arr[2] = 3
          arr[3] = 4
          for (i in 0 to 2) {
              for (j in i + 1 to 3) {
                  arr[i] = arr[i] + arr[j]
              }
          }
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `array swap and string builtins compiles`() {
    assertCompiles(
      """
      fun main(): Unit {
          val arr: Int[2]
          arr[0] = 1
          arr[1] = 2
          var tmp: Int = arr[0]
          arr[0] = arr[1]
          arr[1] = tmp
          var s: String = ""
          intToString(arr[0], s, 1024)
          println(s)
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `while countdown loop compiles`() {
    assertCompiles(
      """
      fun countdown(start: Int): Int {
          var n: Int = start
          var sum: Int = 0
          while (n > 0) {
              sum = sum + n
              n = n - 1
          }
          return sum
      }
      fun main(): Unit {
          var result: Int = countdown(3)
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `while false zero-trip compiles`() {
    assertCompiles(
      """
      fun main(): Unit {
          var marker: Int = 7
          while (false) {
              marker = 0
          }
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `nested while with early return compiles`() {
    assertCompiles(
      """
      fun scan(arr: Int[], size: Int): Int {
          var i: Int = 0
          while (i < size) {
              if (arr[i] < 0) {
                  return arr[i]
              }
              i = i + 1
          }
          return 0
      }
      fun main(): Unit {
          val data: Int[2]
          data[0] = 1
          data[1] = 2
          var hit: Int = scan(data, 2)
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `while with int condition is a semantic rejection`() {
    assertSemanticRejects(
      """
      fun main(): Unit {
          while (1) { }
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `while with unknown variable in condition is a semantic rejection`() {
    assertSemanticRejects(
      """
      fun main(): Unit {
          while (ghost > 0) { }
      }
      """.trimIndent(),
    )
  }

  // endregion

  // region Desired behavior — compiler defects (tests fail until fixed)

  @Test
  fun `empty array initializer for zero-sized array compiles`() {
    assertCompiles(
      """
      fun main(): Unit {
          val arr: Int[0] = []
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `empty array initializer with wrong size is a semantic rejection`() {
    assertSemanticRejects(
      """
      fun main(): Unit {
          val arr: Int[2] = []
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `scalar read with array syntax is a semantic rejection`() {
    assertSemanticRejects(
      """
      fun main(): Unit {
          val x: Int = 0
          val y: Int = x[0]
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `scalar read in return is a semantic rejection`() {
    assertSemanticRejects(
      """
      fun pick(x: Int): Int {
          return x[0]
      }
      fun main(): Unit { }
      """.trimIndent(),
    )
  }

  @Test
  fun `scalar read passed to function is a semantic rejection`() {
    assertSemanticRejects(
      """
      fun echo(n: Int): Unit { }
      fun main(): Unit {
          val x: Int = 0
          echo(x[0])
      }
      """.trimIndent(),
    )
  }

  // endregion
}
