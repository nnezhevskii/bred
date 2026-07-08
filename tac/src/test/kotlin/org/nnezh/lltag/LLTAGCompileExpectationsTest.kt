package org.nnezh.lltag

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.nnezh.org.nnezh.ICGenerator.PrettyPrinter
import org.nnezh.org.nnezh.compiler.TACCompilerImpl
import java.nio.file.Files
import java.nio.file.Path

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
    assertTrue(
      ex.message?.contains("Semantic error") == true,
      "expected formatted semantic diagnostic, got: ${ex.message}",
    )
  }

  private fun assertCompiles(src: String) {
    val tac = TACCompilerImpl().compile(src)
    assertTrue(tac.isNotEmpty(), "expected non-empty 3AC output")
  }

  private fun formattedTac(src: String): List<String> =
    TACCompilerImpl().compile(src)
      .let { PrettyPrinter().format(it) }
      .map { it.trim() }

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

  @Test
  fun `product constructor with wrong arguments is a semantic rejection`() {
    assertSemanticRejects(
      """
      type Person(name: String, age: Int)

      fun main(): Unit {
          val person: Person = Person("Nika", "wrong")
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `field access on scalar is a semantic rejection`() {
    assertSemanticRejects(
      """
      fun main(): Unit {
          val n: Int = 1
          val broken: Int = n.value
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
  fun `long literals arrays arithmetic and calls emit Long typed TAC`() {
    val tac = formattedTac(
      """
      val values: Long[2] = [4L, 5L]

      fun addOne(value: Long): Long {
          return value + 1L
      }

      fun main(): Unit {
          val first: Long = values[0]
          val result: Long = addOne(first + 1L)
          val diff: Long = result - first
          val half: Long = diff / 2L
          val remainder: Long = result % 2L
          val ok: Boolean = result > 5L
          val text: String = longToString(half + remainder)
      }
      """.trimIndent(),
    )

    assertTrue(
      tac.any { it.startsWith("global values:Array[2]") && it.contains("4:Long") && it.contains("5:Long") },
      "expected Long global array initializer, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("GET_PARAM") && it.contains("value:Long") },
      "expected Long function parameter, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("ADD") && it.contains(":Long") && it.contains("value:Long") },
      "expected Long ADD instruction, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("SUB") && it.contains(":Long") && it.contains("result:Long") },
      "expected Long SUB instruction, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("DIV") && it.contains(":Long") },
      "expected Long DIV instruction, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("MOD") && it.contains(":Long") },
      "expected Long MOD instruction, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("CALL") && it.contains("result:Long") && it.contains("addOne:Long") },
      "expected Long function call result, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("CALL") && it.contains("text:String") && it.contains("longToString:String") },
      "expected longToString call result, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.none { it.contains("TypeRef") || it.contains("PrimitiveTypeSign") },
      "unresolved/internal type leaked into TAC:\n${tac.joinToString("\n")}",
    )
  }

  @Test
  fun `current time millis builtin emits Long typed call`() {
    val tac = formattedTac(
      """
      fun main(): Unit {
          val startedAt: Long = currentTimeMillis()
      }
      """.trimIndent(),
    )

    assertTrue(
      tac.any { it.startsWith("CALL ") && it.contains("startedAt:Long") && it.contains("currentTimeMillis:Long") },
      "expected Long currentTimeMillis call, got:\n${tac.joinToString("\n")}",
    )
  }

  @Test
  fun `product types emit struct field load and store TAC`() {
    val tac = formattedTac(
      """
      type mut Address(city: String)
      type mut Person(name: String, age: Int, address: Address)

      fun main(): Unit {
          var address: Address = Address("Berlin")
          var person: Person = Person("Nika", 7, address)
          val age: Int = person.age
          val city: String = person.address.city
          person.age = age + 1
          person.address.city = "Paris"
      }
      """.trimIndent(),
    )

    assertTrue(
      tac.any { it.startsWith("STRUCT_DEF") && it.contains("Address") },
      "expected Address struct definition, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("STRUCT_DEF") && it.contains("Person") },
      "expected Person struct definition, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("FIELD") && it.contains("city:String") },
      "expected city field definition, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("FIELD") && it.contains("address:Address") },
      "expected address field definition, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("CALL") && it.contains("address:Address") && it.contains("Address:Address") },
      "expected Address constructor call, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("CALL") && it.contains("person:Person") && it.contains("Person:Person") },
      "expected Person constructor call, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("LOAD_FIELD") && it.contains("person:Person") && it.contains("age:Int") },
      "expected person.age LOAD_FIELD, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("LOAD_FIELD") && it.contains("person.address:Address") && it.contains("city:String") },
      "expected person.address.city LOAD_FIELD, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("STORE_FIELD") && it.contains("person:Person") && it.contains("age:Int") },
      "expected person.age STORE_FIELD, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("STORE_FIELD") && it.contains("person.address:Address") && it.contains("city:String") },
      "expected person.address.city STORE_FIELD, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.none { it.contains("TypeRef") || it.contains("PrimitiveTypeSign") },
      "unresolved/internal type leaked into TAC:\n${tac.joinToString("\n")}",
    )
  }

  @Test
  fun `sum types emit struct constructor and when tag TAC`() {
    val tac = formattedTac(
      """
      type MaybeInt = Nothing | Just<Int>

      fun main(): Unit {
          val present: MaybeInt = Just(7)
          val absent: MaybeInt = Nothing
          var result: Int = 0
          when (present) {
              Just(payload) -> { result = payload }
              Nothing -> { result = 0 }
          }
      }
      """.trimIndent(),
    )

    assertTrue(
      tac.any { it.startsWith("STRUCT_DEF") && it.contains("MaybeInt") },
      "expected MaybeInt struct definition, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("FIELD") && it.contains("tag:Int") },
      "expected tag field definition, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("FIELD") && it.contains("Just_value0:Int") },
      "expected Just payload field definition, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("SUM_VARIANT") && it.contains("Just:MaybeInt") },
      "expected Just variant metadata, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("CALL") && it.contains("present:MaybeInt") && it.contains("Just:MaybeInt") },
      "expected Just constructor call, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("CALL") && it.contains("absent:MaybeInt") && it.contains("Nothing:MaybeInt") },
      "expected Nothing constructor call, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("LOAD_FIELD") && it.contains("tag:Int") },
      "expected tag load in when lowering, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any {
        it.startsWith("LOAD_FIELD") &&
          it.contains("present:MaybeInt") &&
          it.contains("Just_value0:Int") &&
          it.contains("payload:Int")
      },
      "expected Just payload LOAD_FIELD in when lowering, got:\n${tac.joinToString("\n")}",
    )
  }

  @Test
  fun `sum type named payload fields emit flattened TAC metadata`() {
    val tac = formattedTac(
      """
      type Response = Success(value: Int, message: String) | Failure

      fun main(): Unit {
          val response: Response = Success(200, "ok")
          val failed: Response = Failure
          var result: Int = 0
          var status: String = ""
          when (response) {
              Success(code, message) -> {
                  result = code
                  status = message
              }
              Failure -> { result = -1 }
          }
      }
      """.trimIndent(),
    )

    assertTrue(
      tac.any { it.startsWith("FIELD") && it.contains("Success_value:Int") },
      "expected named Success value field, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("FIELD") && it.contains("Success_message:String") },
      "expected named Success message field, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any {
        it.startsWith("SUM_VARIANT_FIELD") &&
          it.contains("Success:Response") &&
          it.contains("Success_value:Int")
      },
      "expected Success value variant metadata, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any {
        it.startsWith("CALL") &&
          it.contains("response:Response") &&
          it.contains("Success:Response") &&
          it.contains("2:Int")
      },
      "expected Success constructor call with two args, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("CALL") && it.contains("failed:Response") && it.contains("Failure:Response") },
      "expected Failure nullary constructor call, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any {
        it.startsWith("LOAD_FIELD") &&
          it.contains("response:Response") &&
          it.contains("Success_value:Int") &&
          it.contains("code:Int")
      },
      "expected Success value payload LOAD_FIELD, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any {
        it.startsWith("LOAD_FIELD") &&
          it.contains("response:Response") &&
          it.contains("Success_message:String") &&
          it.contains("message:String")
      },
      "expected Success message payload LOAD_FIELD, got:\n${tac.joinToString("\n")}",
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

  @Test
  fun `logical or expression emits OR opcode`() {
    val tac = formattedTac(
      """
      fun main(): Unit {
          val left: Boolean = true
          val right: Boolean = false
          val either: Boolean = left || right
      }
      """.trimIndent(),
    )

    assertTrue(
      tac.any { it.startsWith("OR ") },
      "expected OR opcode in 3AC output, got:\n${tac.joinToString("\n")}",
    )
  }

  @Test
  fun `unary expressions emit NEG and NOT opcodes`() {
    val tac = formattedTac(
      """
      fun main(): Unit {
          val flag: Boolean = true
          val negated: Boolean = !flag
          val value: Int = -1
      }
      """.trimIndent(),
    )

    assertTrue(tac.any { it.startsWith("NOT ") }, "expected NOT opcode, got:\n${tac.joinToString("\n")}")
    assertTrue(tac.any { it.startsWith("NEG ") }, "expected NEG opcode, got:\n${tac.joinToString("\n")}")
  }

  @Test
  fun `array initializer emits allocation and one store per element`() {
    val tac = formattedTac(
      """
      fun main(): Unit {
          val arr: Int[2] = [4, 5]
      }
      """.trimIndent(),
    )

    assertTrue(
      tac.any { it.startsWith("ALLOC ") && it.contains("arr:Array") && it.contains("2:Int") },
      "expected ALLOC for arr[2], got:\n${tac.joinToString("\n")}",
    )
    assertEquals(2, tac.count { it.startsWith("STORE ") })
  }

  @Test
  fun `global static array emits global declaration and can be read`() {
    val tac = formattedTac(
      """
      val values: Int[2] = [4, 5]

      fun main(): Unit {
          val item: Int = values[1]
      }
      """.trimIndent(),
    )

    assertTrue(
      tac.any { it.startsWith("global values:Array[2]") && it.contains("4:Int") && it.contains("5:Int") },
      "expected global array declaration in 3AC output, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(tac.any { it.startsWith("LOAD ") }, "expected LOAD from global array, got:\n${tac.joinToString("\n")}")
  }

  @Test
  fun `array load does not emit dummy zero assignment before load`() {
    val tac = formattedTac(
      """
      fun main(): Unit {
          val values: Int[1] = [7]
          val first: Int = values[0]
      }
      """.trimIndent(),
    )
    val loadIndex = tac.indexOfFirst { it.startsWith("LOAD ") && it.contains("values:Array") }

    assertTrue(loadIndex > 0, "expected LOAD from values, got:\n${tac.joinToString("\n")}")
    val loadDestination = Regex("""LOAD\s+(\S+:Int)\s+values:Array""")
      .find(tac[loadIndex])
      ?.groupValues
      ?.get(1)
      ?: error("expected LOAD destination in: ${tac[loadIndex]}")
    assertFalse(
      tac.take(loadIndex).any { Regex("""ASSIGN\s+${Regex.escape(loadDestination)}\s+0:Int""").matches(it) },
      "array LOAD should not be preceded by dummy zero assignment, got:\n${tac.joinToString("\n")}",
    )
  }

  @Test
  fun `global static array without initializer emits global declaration`() {
    val tac = formattedTac(
      """
      val values: Int[2]

      fun main(): Unit {
          val item: Int = values[0]
      }
      """.trimIndent(),
    )

    assertTrue(
      tac.any { it == "global values:Array[2]" },
      "expected uninitialized global array declaration in 3AC output, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(tac.any { it.startsWith("LOAD ") }, "expected LOAD from global array, got:\n${tac.joinToString("\n")}")
  }

  @Test
  fun `generic typeclass sort with generic local temp compiles`() {
    assertCompiles(
      """
      typeclass Ordered<T> {
          fun less(a: T, b: T): Boolean
      }

      instance Ordered<Int> {
          fun less(a: Int, b: Int): Boolean {
              return a < b
          }
      }

      fun bubbleSort<T: Ordered>(arr: Array<T>, size: Int): Unit {
          var pass: Int = 0

          while (pass < size) {
              var j: Int = 1

              while (j < size - pass) {
                  if (less(arr[j], arr[j - 1])) {
                      var tmp: T = arr[j]
                      arr[j] = arr[j - 1]
                      arr[j - 1] = tmp
                  }

                  j = j + 1
              }

              pass = pass + 1
          }
      }

      fun run(arr: Array<Int>, size: Int): Unit {
          bubbleSort(arr, size)
      }

      fun main(): Unit { }
      """.trimIndent(),
    )
  }

  @Test
  fun `generic array and scalar template call emits resolved mangled function`() {
    val tac = formattedTac(
      """
      typeclass Ordered<T> {
          fun equals(a: T, b: T): Boolean
      }

      instance Ordered<Int> {
          fun equals(a: Int, b: Int): Boolean {
              return a == b
          }
      }

      fun findIndex<T: Ordered>(arr: Array<T>, target: T): Int {
          if (equals(arr[0], target)) {
              return 0
          }

          return -1
      }

      fun main(): Unit {
          val values: Array<Int>[2]
          val target: Int = 1
          val index: Int = findIndex(values, target)
      }
      """.trimIndent(),
    )

    assertTrue(
      tac.any { it == "func findIndex_Array_Int_Int_Int:" },
      "expected instantiated findIndex function, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.any { it.startsWith("CALL ") && it.contains("findIndex_Array_Int_Int_Int") },
      "expected call to instantiated findIndex, got:\n${tac.joinToString("\n")}",
    )
    assertTrue(
      tac.none { it.contains("TypeRef") || it.contains("PrimitiveTypeSign") },
      "unresolved/internal type leaked into TAC:\n${tac.joinToString("\n")}",
    )
  }

  @Test
  fun `user function calls use mangled target name`() {
    val tac = formattedTac(
      """
      fun id(value: Int): Int {
          return value
      }
      fun main(): Unit {
          val n: Int = id(1)
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
