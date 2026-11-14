package org.nnezh

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.bred.ast.ArrayDeclarationASTNode
import org.nnezh.bred.ast.ArrayElementAccessASTNode
import org.nnezh.bred.ast.ArrayInitializationExpressionASTNode
import org.nnezh.bred.ast.AssignmentStatementAstNode
import org.nnezh.bred.ast.BinaryExpressionASTNode
import org.nnezh.bred.ast.BlockAstNode
import org.nnezh.bred.ast.DeclareGlobalVariableASTNode
import org.nnezh.bred.ast.FieldAccessChainExpressionASTNode
import org.nnezh.bred.ast.FunctionCallExpressionASTNode
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.ReturnFunctionStatementAstNode
import org.nnezh.bred.ast.ScalarVariableInitializationASTNode
import org.nnezh.bred.ast.UnaryExpressionASTNode
import org.nnezh.bred.ast.VariableExpressionASTNode
import org.nnezh.bred.ast.WhenExpressionASTNode
import org.nnezh.bred.common.SymbolId

class SemanticAnalyzerTest {
    // region Positive baseline

    @Test
    fun `empty program has no semantic diagnostics`() {
        assertNoSemanticDiagnostics("")
    }

    @Test
    fun `primitive local initializers have no semantic diagnostics`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val i: Int = 1
                val d: Double = 1.0
                val s: String = "bred"
                val b: Boolean = true
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `global primitive initializer uses resolved context type`() {
        assertNoSemanticDiagnostics(
            """
            val ProgramName: String = "Bred data analyzer"

            fun main(): Unit {
                println(ProgramName)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `function arguments are visible in body`() {
        assertNoSemanticDiagnostics(
            """
            fun id(value: Int): Int {
                return value
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `template generic inferred from array and scalar arguments has no semantic diagnostics`() {
        assertNoSemanticDiagnosticsAfterTemplateInstantiation(
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
    }

    @Test
    fun `forward function reference is valid`() {
        assertNoSemanticDiagnostics(
            """
            fun first(): Unit {
                second()
            }
            fun second(): Unit { }
            """.trimIndent(),
        )
    }

    @Test
    fun `int arithmetic and boolean comparison are valid`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val sum: Int = 1 + 2 * 3
                val ok: Boolean = sum > 0
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `long arithmetic modulo unary minus and comparison are valid`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                var total: Long = 1L
                val delta: Long = 2L
                total = total + delta * 3L
                total = total - 1L
                val half: Long = total / 2L
                val remainder: Long = total % 2L
                val negated: Long = -remainder
                val ok: Boolean = negated <= half
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `generic function can be instantiated with Long literal`() {
        assertNoSemanticDiagnosticsAfterTemplateInstantiation(
            """
            fun id<T>(value: T): T {
                return value
            }

            fun main(): Unit {
                val result: Long = id(7L)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `boolean conditions are accepted in if and while`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                var keepGoing: Boolean = true
                if (keepGoing) {
                    println("if")
                }
                while (keepGoing) {
                    keepGoing = false
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `function call result type can initialize a variable`() {
        assertNoSemanticDiagnostics(
            """
            fun one(): Int {
                return 1
            }
            fun main(): Unit {
                val n: Int = one()
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `supported builtin calls have no semantic diagnostics`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val n: Int = readInt()
                val same: Boolean = stringEquals("a", "a")
                if (same) {
                    println("ok")
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `runtime string conversion builtins return string values`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val input: String = readString()
                val n: Int = stringToInt("42")
                val intText: String = intToString(n)
                val longText: String = longToString(42L)
                val doubleText: String = doubleToString(1.5)
                println(input + intText + longText + doubleText)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `current time millis builtin returns Long`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val now: Long = currentTimeMillis()
                val later: Boolean = now >= 0L
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `int literal cannot initialize Long without long suffix`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val n: Long = 1
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            ScalarVariableInitializationASTNode::class.java,
        )
    }

    // endregion

    // region Scope and variables

    @Test
    fun `unknown variable in initializer is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val n: Int = missing
            }
            """.trimIndent(),
            SemanticErrorType.UNKNOWN_VARIABLE,
            VariableExpressionASTNode::class.java,
        )
    }

    @Test
    fun `self reference in scalar initializer is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val a: Int = a + 1
            }
            """.trimIndent(),
            SemanticErrorType.UNKNOWN_VARIABLE,
            VariableExpressionASTNode::class.java,
        )
    }

    @Test
    fun `variable redeclaration in same block is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val n: Int = 1
                val n: Int = 2
            }
            """.trimIndent(),
            SemanticErrorType.VARIABLE_REDECLARATION,
            ScalarVariableInitializationASTNode::class.java,
        )
    }

    @Test
    fun `shadowing outer variable is reported as warning`() {
        assertSemanticWarning(
            """
            fun main(): Unit {
                val n: Int = 1
                if (true) {
                    val n: Int = 2
                }
            }
            """.trimIndent(),
            SemanticErrorType.VARIABLE_OVERSHADOW,
            ScalarVariableInitializationASTNode::class.java,
        )
    }

    @Test
    fun `shadowing initializer cannot read the variable being declared`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val a: Int = 1
                if (true) {
                    val a: Int = a + 1
                }
            }
            """.trimIndent(),
            SemanticErrorType.UNKNOWN_VARIABLE,
            VariableExpressionASTNode::class.java,
        )
    }

    @Test
    fun `global variable is visible in function body`() {
        assertNoSemanticDiagnostics(
            """
            val answer: Int = 42
            fun main(): Unit {
                val n: Int = answer
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `global variable initializer cannot read the variable being declared`() {
        assertSingleSemanticError(
            """
            val answer: Int = answer + 1
            fun main(): Unit { }
            """.trimIndent(),
            SemanticErrorType.UNKNOWN_VARIABLE,
            VariableExpressionASTNode::class.java,
        )
    }

    @Test
    fun `global variable initializer is type checked`() {
        assertSingleSemanticError(
            """
            val answer: Int = "wrong"
            fun main(): Unit { }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
        )
    }

    @Test
    fun `global static array can be passed to array parameter`() {
        assertNoSemanticDiagnostics(
            """
            val values: Int[2] = [1, 2]

            fun sum(arr: Int[], size: Int): Int {
                return arr[0] + arr[1]
            }

            fun main(): Unit {
                val total: Int = sum(values, 2)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `global static array without initializer can be read and passed`() {
        assertNoSemanticDiagnostics(
            """
            val values: Int[2]

            fun first(arr: Int[]): Int {
                return arr[0]
            }

            fun main(): Unit {
                val a: Int = values[0]
                val b: Int = first(values)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `global static array accepts explicit Array type form`() {
        assertNoSemanticDiagnostics(
            """
            val values: Array<Int>[2] = [1, 2]

            fun main(): Unit {
                val first: Int = values[0]
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `global static array cannot initialize scalar variable`() {
        assertSingleSemanticError(
            """
            val values: Int[2] = [1, 2]

            fun main(): Unit {
                val scalar: Int = values
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            ScalarVariableInitializationASTNode::class.java,
        )
    }

    @Test
    fun `global static array initializer size must match declaration`() {
        assertSingleSemanticError(
            """
            val values: Int[2] = [1, 2, 3]

            fun main(): Unit { }
            """.trimIndent(),
            SemanticErrorType.INVALID_AMOUNT_OF_ARGUMENTS_IN_ARRAYS_INITIALIZATION,
        )
    }

    @Test
    fun `global static array initializer element type must match declaration`() {
        assertSingleSemanticError(
            """
            val values: Int[2] = [true, false]

            fun main(): Unit { }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
        )
    }

    @Test
    fun `global static array initializer cannot read the array being declared`() {
        assertSingleSemanticError(
            """
            val values: Int[1] = [values[0]]

            fun main(): Unit { }
            """.trimIndent(),
            SemanticErrorType.UNKNOWN_VARIABLE,
            ArrayElementAccessASTNode::class.java,
        )
    }

    @Test
    fun `global variable redeclaration is a semantic error`() {
        assertSingleSemanticError(
            """
            val answer: Int = 1
            val answer: Int = 2
            fun main(): Unit { }
            """.trimIndent(),
            SemanticErrorType.VARIABLE_REDECLARATION,
        )
    }

    @Test
    fun `assignment to immutable global val is a semantic error`() {
        assertSingleSemanticError(
            """
            val answer: Int = 1
            fun main(): Unit {
                answer = 2
            }
            """.trimIndent(),
            SemanticErrorType.VARIABLE_CHANGING_IMMUTABLE,
            AssignmentStatementAstNode::class.java,
        )
    }

    @Test
    fun `mutable variable assignment with matching type is valid`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                var n: Int = 1
                n = 2
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `assignment to immutable scalar val is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val n: Int = 1
                n = 2
            }
            """.trimIndent(),
            SemanticErrorType.VARIABLE_CHANGING_IMMUTABLE,
            AssignmentStatementAstNode::class.java,
        )
    }

    @Test
    fun `assignment with wrong rhs type is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                var n: Int = 1
                n = "wrong"
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            AssignmentStatementAstNode::class.java,
        )
    }

    // endregion

    // region Product types and fields

    @Test
    fun `product constructor and nested field reads are valid`() {
        assertNoSemanticDiagnostics(
            """
            type Address(city: String)
            type Person(name: String, age: Int, address: Address)

            fun main(): Unit {
                val address: Address = Address("Berlin")
                val person: Person = Person("Nika", 7, address)
                val city: String = person.address.city
                val age: Int = person.age
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `product field assignment with matching type is valid`() {
        assertNoSemanticDiagnostics(
            """
            type mut Counter(value: Int)

            fun main(): Unit {
                val counter: Counter = Counter(1)
                counter.value = 2
                val value: Int = counter.value
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `product field type may reference a later type declaration`() {
        assertNoSemanticDiagnostics(
            """
            type Person(name: String, address: Address)
            type Address(city: String)

            fun main(): Unit {
                val person: Person = Person("Nika", Address("Berlin"))
                val city: String = person.address.city
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `generic product field access is valid after template instantiation`() {
        assertNoSemanticDiagnosticsAfterTemplateInstantiation(
            """
            type Box<T>(value: T)

            fun unwrap<T>(box: Box<T>): T {
                return box.value
            }

            fun main(): Unit {
                val box: Box<Int> = Box<Int>(1)
                val value: Int = unwrap<Int>(box)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nested product field assignment uses final owner mutability`() {
        assertNoSemanticDiagnostics(
            """
            type Person(address: Address)
            type mut Address(city: String)

            fun main(): Unit {
                val person: Person = Person(Address("Berlin"))
                person.address.city = "Paris"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nested product field assignment rejects immutable final owner`() {
        assertSingleSemanticError(
            """
            type mut Person(address: Address)
            type Address(city: String)

            fun main(): Unit {
                val person: Person = Person(Address("Berlin"))
                person.address.city = "Paris"
            }
            """.trimIndent(),
            SemanticErrorType.VARIABLE_CHANGING_IMMUTABLE,
            AssignmentStatementAstNode::class.java,
        )
    }

    @Test
    fun `immutable product field assignment is a semantic error`() {
        assertSingleSemanticError(
            """
            type Counter(value: Int)

            fun main(): Unit {
                var counter: Counter = Counter(1)
                counter.value = 2
            }
            """.trimIndent(),
            SemanticErrorType.VARIABLE_CHANGING_IMMUTABLE,
            AssignmentStatementAstNode::class.java,
        )
    }

    @Test
    fun `constructor with wrong argument type is a semantic error`() {
        assertSingleSemanticError(
            """
            type Person(name: String, age: Int)

            fun main(): Unit {
                val person: Person = Person("Nika", "wrong")
            }
            """.trimIndent(),
            SemanticErrorType.CONSTRUCTOR_IS_FOUND_BUT_WRONG_ARGUMENTS,
            FunctionCallExpressionASTNode::class.java,
        )
    }

    @Test
    fun `unknown product field is a semantic error`() {
        assertSingleSemanticError(
            """
            type Person(name: String)

            fun main(): Unit {
                val person: Person = Person("Nika")
                val age: Int = person.age
            }
            """.trimIndent(),
            SemanticErrorType.UNKNOWN_FIELD,
            FieldAccessChainExpressionASTNode::class.java,
        )
    }

    @Test
    fun `field access on scalar is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val n: Int = 1
                val broken: Int = n.value
            }
            """.trimIndent(),
            SemanticErrorType.UNKNOWN_FIELD,
            FieldAccessChainExpressionASTNode::class.java,
        )
    }

    @Test
    fun `product field assignment checks rhs type`() {
        assertSingleSemanticError(
            """
            type mut Counter(value: Int)

            fun main(): Unit {
                var counter: Counter = Counter(1)
                counter.value = "wrong"
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            AssignmentStatementAstNode::class.java,
        )
    }

    // endregion

    // region Sum types and when

    @Test
    fun `sum constructors initialize sum variables`() {
        assertNoSemanticDiagnostics(
            """
            type MaybeInt = Nothing | Just<Int>

            fun main(): Unit {
                val present: MaybeInt = Just(1)
                val absent: MaybeInt = Nothing
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `sum constructor with named payload fields initializes sum variable`() {
        assertNoSemanticDiagnostics(
            """
            type Response = Success(value: Int, message: String) | Failure

            fun main(): Unit {
                val response: Response = Success(200, "ok")
                val failed: Response = Failure
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `sum constructor checks payload type`() {
        assertSingleSemanticError(
            """
            type MaybeInt = Nothing | Just<Int>

            fun main(): Unit {
                val broken: MaybeInt = Just("wrong")
            }
            """.trimIndent(),
            SemanticErrorType.CONSTRUCTOR_IS_FOUND_BUT_WRONG_ARGUMENTS,
            FunctionCallExpressionASTNode::class.java,
        )
    }

    @Test
    fun `when over sum must cover all variants`() {
        assertSingleSemanticError(
            """
            type MaybeInt = Nothing | Just<Int>

            fun main(): Unit {
                val value: MaybeInt = Just(1)
                var result: Int = 0
                when (value) {
                    Just -> { result = 1 }
                }
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            WhenExpressionASTNode::class.java,
        )
    }

    @Test
    fun `exhaustive when over sum is valid`() {
        assertNoSemanticDiagnostics(
            """
            type MaybeInt = Nothing | Just<Int>

            fun main(): Unit {
                val value: MaybeInt = Just(1)
                var result: Int = 0
                when (value) {
                    Just -> { result = 1 }
                    Nothing -> { result = 0 }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `when over sum binds positional payload in branch body`() {
        assertNoSemanticDiagnostics(
            """
            type MaybeInt = Nothing | Just<Int>

            fun main(): Unit {
                val maybe: MaybeInt = Just(41)
                var result: Int = 0
                when (maybe) {
                    Just(payload) -> { result = payload + 1 }
                    Nothing -> { result = -1 }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `when over sum binds named payload fields positionally`() {
        assertNoSemanticDiagnostics(
            """
            type Response = Success(value: Int, message: String) | Failure

            fun main(): Unit {
                val response: Response = Success(200, "ok")
                var result: Int = 0
                when (response) {
                    Success(code, text) -> {
                        val copy: String = text
                        result = code
                    }
                    Failure -> { result = -1 }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `when over sum rejects payload binding count mismatch`() {
        assertSingleSemanticError(
            """
            type MaybeInt = Nothing | Just<Int>

            fun main(): Unit {
                val maybe: MaybeInt = Just(1)
                when (maybe) {
                    Just(payload, extra) -> { println("present") }
                    Nothing -> { println("empty") }
                }
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            FunctionCallExpressionASTNode::class.java,
        )
    }

    @Test
    fun `when over sum rejects non variable payload binding`() {
        assertSingleSemanticError(
            """
            type MaybeInt = Nothing | Just<Int>

            fun main(): Unit {
                val maybe: MaybeInt = Just(1)
                when (maybe) {
                    Just(1) -> { println("present") }
                    Nothing -> { println("empty") }
                }
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
        )
    }

    @Test
    fun `when over sum rejects duplicate payload binding name`() {
        assertSingleSemanticError(
            """
            type PairBox = Empty | Pair<Int, Int>

            fun main(): Unit {
                val box: PairBox = Pair(1, 2)
                when (box) {
                    Pair(item, item) -> { println(intToString(item)) }
                    Empty -> { println("empty") }
                }
            }
            """.trimIndent(),
            SemanticErrorType.VARIABLE_REDECLARATION,
            VariableExpressionASTNode::class.java,
        )
    }

    @Test
    fun `when over sum rejects payload binding that redeclares visible variable`() {
        assertSingleSemanticError(
            """
            type MaybeInt = Nothing | Just<Int>

            fun main(): Unit {
                val payload: Int = 0
                val maybe: MaybeInt = Just(1)
                when (maybe) {
                    Just(payload) -> { println(intToString(payload)) }
                    Nothing -> { println("empty") }
                }
            }
            """.trimIndent(),
            SemanticErrorType.VARIABLE_REDECLARATION,
            VariableExpressionASTNode::class.java,
        )
    }

    @Test
    fun `when payload binding is scoped to its branch body`() {
        assertSingleSemanticError(
            """
            type MaybeInt = Nothing | Just<Int>

            fun main(): Unit {
                val maybe: MaybeInt = Just(1)
                when (maybe) {
                    Just(payload) -> { println(intToString(payload)) }
                    Nothing -> { println("empty") }
                }
                val leaked: Int = payload
            }
            """.trimIndent(),
            SemanticErrorType.UNKNOWN_VARIABLE,
            VariableExpressionASTNode::class.java,
        )
    }

    @Test
    fun `when over sum may use else for remaining variants`() {
        assertNoSemanticDiagnostics(
            """
            type MaybeInt = Nothing | Just<Int>

            fun main(): Unit {
                val value: MaybeInt = Just(1)
                var result: Int = 0
                when (value) {
                    Just -> { result = 1 }
                    else -> { result = 0 }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `when over sum rejects duplicate variant branch`() {
        assertSingleSemanticError(
            """
            type MaybeInt = Nothing | Just<Int>

            fun main(): Unit {
                val value: MaybeInt = Just(1)
                when (value) {
                    Just -> { println("first") }
                    Just -> { println("second") }
                    Nothing -> { println("empty") }
                }
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            VariableExpressionASTNode::class.java,
        )
    }

    @Test
    fun `when over sum rejects unknown variant branch`() {
        assertSingleSemanticError(
            """
            type MaybeInt = Nothing | Just<Int>

            fun main(): Unit {
                val value: MaybeInt = Just(1)
                when (value) {
                    Other -> { println("other") }
                    Nothing -> { println("empty") }
                    Just -> { println("present") }
                }
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            VariableExpressionASTNode::class.java,
        )
    }

    @Test
    fun `generic sum constructor is valid after template instantiation`() {
        assertNoSemanticDiagnosticsAfterTemplateInstantiation(
            """
            type Maybe<T> = Just<T>(v: T) | Nothing

            fun id<T>(value: T): Maybe<T> {
                return Just<T>(value)
            }

            fun main(): Unit {
                val x: Maybe<Int> = id<Int>(200)
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region Functions and overloads

    @Test
    fun `unknown function call is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                missing()
            }
            """.trimIndent(),
            SemanticErrorType.FUNCTION_NOT_FOUND,
            FunctionCallExpressionASTNode::class.java,
        )
    }

    @Test
    fun `function call with wrong argument type is a semantic error`() {
        assertSingleSemanticError(
            """
            fun takeInt(n: Int): Unit { }
            fun main(): Unit {
                takeInt("wrong")
            }
            """.trimIndent(),
            SemanticErrorType.FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS,
            FunctionCallExpressionASTNode::class.java,
        )
    }

    @Test
    fun `function overload is resolved by argument types`() {
        assertNoSemanticDiagnostics(
            """
            fun pick(n: Int): Int {
                return n
            }
            fun pick(s: String): String {
                return s
            }
            fun main(): Unit {
                val n: Int = pick(1)
                val s: String = pick("s")
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `duplicate function signature is a semantic error`() {
        assertSingleSemanticError(
            """
            fun pick(n: Int): Int {
                return n
            }
            fun pick(value: Int): String {
                return "duplicate"
            }
            """.trimIndent(),
            SemanticErrorType.REDEFINE_FUNCTION,
            FunctionDeclAstNode::class.java,
        )
    }

    @Test
    fun `user function cannot redefine builtin signature`() {
        assertSingleSemanticError(
            """
            fun println(value: String): Unit { }
            """.trimIndent(),
            SemanticErrorType.REDEFINE_FUNCTION,
            FunctionDeclAstNode::class.java,
        )
    }

    @Test
    fun `function name used as value is not a first class function`() {
        assertSemanticError(
            """
            fun callback(): Unit { }
            fun takeInt(n: Int): Unit { }
            fun main(): Unit {
                takeInt(callback)
            }
            """.trimIndent(),
            SemanticErrorType.UNKNOWN_VARIABLE,
            VariableExpressionASTNode::class.java,
        )
    }

    // endregion

    // region Type checking

    @Test
    fun `initializer type mismatch is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val n: Int = "wrong"
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            ScalarVariableInitializationASTNode::class.java,
        )
    }

    @Test
    fun `mixed int and double arithmetic is rejected without promotion`() {
        assertSemanticError(
            """
            fun main(): Unit {
                val n: Int = 1 + 2.0
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            BinaryExpressionASTNode::class.java,
        )
    }

    @Test
    fun `string concatenation and string comparisons are valid`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val word: String = "br" + "ed"
                val same: Boolean = word == "bred"
                val ordered: Boolean = "a" <= "b"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `equality requires operands of the same type`() {
        assertSemanticError(
            """
            fun main(): Unit {
                val same: Boolean = 1 == "1"
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            BinaryExpressionASTNode::class.java,
        )
    }

    @Test
    fun `modulo requires integer operands`() {
        assertSemanticError(
            """
            fun main(): Unit {
                val bad: Double = 5.0 % 2.0
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            BinaryExpressionASTNode::class.java,
        )
    }

    @Test
    fun `logical operators require boolean operands`() {
        assertSemanticError(
            """
            fun main(): Unit {
                val bad: Boolean = 1 && 0
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            BinaryExpressionASTNode::class.java,
        )
    }

    @Test
    fun `unary not requires boolean operand`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val bad: Boolean = !1
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            UnaryExpressionASTNode::class.java,
        )
    }

    @Test
    fun `unary not result can initialize boolean variable`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val flag: Boolean = true
                val ok: Boolean = !flag
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `unary minus result can initialize double variable`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val x: Double = -1.0
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `unary expression result can be passed as function argument`() {
        assertNoSemanticDiagnostics(
            """
            fun accept(flag: Boolean): Unit { }
            fun main(): Unit {
                val flag: Boolean = true
                accept(!flag)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `if condition must be boolean`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                if (1) {
                    println("bad")
                }
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
        )
    }

    @Test
    fun `while condition must be boolean`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                while ("bad") { }
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
        )
    }

    // endregion

    // region Arrays

    @Test
    fun `static array declaration without initializer is valid`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val values: Int[3]
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `static array initialization list with matching elements is valid`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val values: Int[3] = [1, 2, 3]
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `static array declaration accepts explicit Array type form`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val values: Array<Int>[2] = [1, 2]
                val first: Int = values[0]
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `static array initialization list rejects mixed element types`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val values: Int[2] = [1, true]
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCONSISTENT_ARRAY_TYPE,
            ArrayInitializationExpressionASTNode::class.java,
        )
    }

    @Test
    fun `static array initialization list size must match declaration size`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val values: Int[2] = [1]
            }
            """.trimIndent(),
            SemanticErrorType.INVALID_AMOUNT_OF_ARGUMENTS_IN_ARRAYS_INITIALIZATION,
            ArrayDeclarationASTNode::class.java,
        )
    }

    @Test
    fun `array read with int index is valid`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val values: Int[2] = [1, 2]
                val first: Int = values[0]
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `array index must be int`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val values: Int[2] = [1, 2]
                val first: Int = values[true]
            }
            """.trimIndent(),
            SemanticErrorType.ARRAY_INDEX_IS_NOT_INTEGER,
            ArrayElementAccessASTNode::class.java,
        )
    }

    @Test
    fun `scalar variable used with array access is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val n: Int = 1
                val bad: Int = n[0]
            }
            """.trimIndent(),
            SemanticErrorType.ARRAY_IS_EXPECTED_BUT_GOT_SCALAR,
            ArrayElementAccessASTNode::class.java,
        )
    }

    @Test
    fun `array initializer cannot read the array being declared`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val values: Int[1] = [values[0]]
            }
            """.trimIndent(),
            SemanticErrorType.UNKNOWN_VARIABLE,
            ArrayElementAccessASTNode::class.java,
        )
    }

    @Test
    fun `array element assignment with matching element type is valid`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val values: Int[2] = [1, 2]
                values[0] = 3
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `array element assignment with incompatible value is a semantic error`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val values: Int[2] = [1, 2]
                values[0] = "bad"
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            AssignmentStatementAstNode::class.java,
        )
    }

    @Test
    fun `array parameter can be read and passed`() {
        assertNoSemanticDiagnostics(
            """
            fun first(values: Int[]): Int {
                return values[0]
            }
            fun main(): Unit {
                val values: Int[2] = [1, 2]
                val n: Int = first(values)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passing scalar where array parameter is expected is a semantic error`() {
        assertSingleSemanticError(
            """
            fun take(values: Int[]): Unit { }
            fun main(): Unit {
                take(1)
            }
            """.trimIndent(),
            SemanticErrorType.FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS,
            FunctionCallExpressionASTNode::class.java,
        )
    }

    // endregion

    // region Returns and control flow

    @Test
    fun `wrong return type is a semantic error`() {
        assertSingleSemanticError(
            """
            fun answer(): Int {
                return "wrong"
            }
            """.trimIndent(),
            SemanticErrorType.METHOD_HAS_WRONG_RETURN,
            ReturnFunctionStatementAstNode::class.java,
        )
    }

    @Test
    fun `non unit function requires explicit return`() {
        assertSingleSemanticError(
            """
            fun answer(): Int { }
            """.trimIndent(),
            SemanticErrorType.EXPLICIT_RETURN_IS_EXPECTED,
            BlockAstNode::class.java,
        )
    }

    @Test
    fun `code after return is a semantic error`() {
        assertSingleSemanticError(
            """
            fun answer(): Int {
                return 1
                val unreachable: Int = 2
            }
            """.trimIndent(),
            SemanticErrorType.BLOCK_CONTAINS_CODE_AFTER_RETURN,
            BlockAstNode::class.java,
        )
    }

    @Test
    fun `multiple returns in one block are a semantic error`() {
        assertSingleSemanticError(
            """
            fun answer(): Int {
                return 1
                return 2
            }
            """.trimIndent(),
            SemanticErrorType.BLOCK_CONTAINS_MORE_THAN_ONE_RETURN,
            BlockAstNode::class.java,
        )
    }

    // endregion

    // region Return and control-flow edge cases

    @Test
    fun `non unit function may return through nested complete if else`() {
        assertNoSemanticDiagnostics(
            """
            fun choose(a: Int, b: Int): Int {
                if (a > 0) {
                    return 1
                } else {
                    if (b > 0) {
                        return 2
                    } else {
                        return 3
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `incomplete nested if is valid when followed by unconditional return`() {
        assertNoSemanticDiagnostics(
            """
            fun choose(a: Int, b: Int): Int {
                if (a > 0) {
                    return 1
                } else {
                    if (b > 0) {
                        return 2
                    }
                }
                return 3
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `incomplete nested if without later return is missing explicit return`() {
        assertSingleSemanticError(
            """
            fun choose(a: Int, b: Int): Int {
                if (a > 0) {
                    return 1
                } else {
                    if (b > 0) {
                        return 2
                    }
                }
            }
            """.trimIndent(),
            SemanticErrorType.EXPLICIT_RETURN_IS_EXPECTED,
            BlockAstNode::class.java,
        )
    }

    @Test
    fun `while return does not satisfy non unit function return requirement`() {
        assertSingleSemanticError(
            """
            fun choose(a: Int): Int {
                while (a > 0) {
                    return a
                }
            }
            """.trimIndent(),
            SemanticErrorType.EXPLICIT_RETURN_IS_EXPECTED,
            BlockAstNode::class.java,
        )
    }

    @Test
    fun `for return does not satisfy non unit function return requirement`() {
        assertSingleSemanticError(
            """
            fun choose(): Int {
                for (i in 0 to 2) {
                    return i
                }
            }
            """.trimIndent(),
            SemanticErrorType.EXPLICIT_RETURN_IS_EXPECTED,
            BlockAstNode::class.java,
        )
    }

    @Test
    fun `unit function returning value is wrong return type`() {
        assertSingleSemanticError(
            """
            fun log(): Unit {
                return 1
            }
            """.trimIndent(),
            SemanticErrorType.METHOD_HAS_WRONG_RETURN,
            ReturnFunctionStatementAstNode::class.java,
        )
    }

    @Test
    fun `unit function accepts bare explicit return`() {
        assertNoSemanticDiagnostics(
            """
            fun log(): Unit {
                return
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `wrong return type inside else branch is reported on return statement`() {
        assertSingleSemanticError(
            """
            fun choose(flag: Boolean): Int {
                if (flag) {
                    return 1
                } else {
                    return "bad"
                }
            }
            """.trimIndent(),
            SemanticErrorType.METHOD_HAS_WRONG_RETURN,
            ReturnFunctionStatementAstNode::class.java,
        )
    }

    @Test
    fun `return after complete if else is multiple return in one block`() {
        assertSingleSemanticError(
            """
            fun choose(flag: Boolean): Int {
                if (flag) {
                    return 1
                } else {
                    return 2
                }
                return 3
            }
            """.trimIndent(),
            SemanticErrorType.BLOCK_CONTAINS_MORE_THAN_ONE_RETURN,
            BlockAstNode::class.java,
        )
    }

    @Test
    fun `ordinary code after complete if else return is unreachable code`() {
        assertSingleSemanticError(
            """
            fun choose(flag: Boolean): Int {
                if (flag) {
                    return 1
                } else {
                    return 2
                }
                val unreachable: Int = 3
            }
            """.trimIndent(),
            SemanticErrorType.BLOCK_CONTAINS_CODE_AFTER_RETURN,
            BlockAstNode::class.java,
        )
    }

    @Test
    fun `ordinary code after return inside nested branch is unreachable code`() {
        assertSingleSemanticError(
            """
            fun choose(flag: Boolean): Int {
                if (flag) {
                    return 1
                    val unreachable: Int = 2
                } else {
                    return 3
                }
            }
            """.trimIndent(),
            SemanticErrorType.BLOCK_CONTAINS_CODE_AFTER_RETURN,
            BlockAstNode::class.java,
        )
    }

    // endregion

    // region Functions and overload edge cases

    @Test
    fun `array and scalar overloads with same arity are distinct`() {
        assertNoSemanticDiagnostics(
            """
            fun take(value: Int): Int {
                return value
            }
            fun take(values: Int[]): Int {
                return values[0]
            }
            fun main(): Unit {
                val values: Int[1] = [7]
                val a: Int = take(1)
                val b: Int = take(values)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `duplicate array parameter function signature is rejected`() {
        assertSingleSemanticError(
            """
            fun take(values: Int[]): Unit { }
            fun take(other: Int[]): Int {
                return other[0]
            }
            """.trimIndent(),
            SemanticErrorType.REDEFINE_FUNCTION,
        )
    }

    @Test
    fun `builtin call with wrong argument type is wrong arguments`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                println(1)
            }
            """.trimIndent(),
            SemanticErrorType.FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS,
            FunctionCallExpressionASTNode::class.java,
        )
    }

    // endregion

    // region Arrays desired behavior and negative edge cases

    @Test
    fun `static array without initializer can be read later`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val values: Int[2]
                val first: Int = values[0]
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `static array without initializer can be passed to array parameter`() {
        assertNoSemanticDiagnostics(
            """
            fun take(values: Int[]): Unit { }
            fun main(): Unit {
                val values: Int[2]
                take(values)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `empty initialization list matches zero sized array`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                val values: Int[0] = []
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `empty initialization list with non zero size is size mismatch`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val values: Int[2] = []
            }
            """.trimIndent(),
            SemanticErrorType.INVALID_AMOUNT_OF_ARGUMENTS_IN_ARRAYS_INITIALIZATION,
            ArrayDeclarationASTNode::class.java,
        )
    }

    @Test
        fun `array initialization element type must match declared element type`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val values: Int[2] = [1.0, 2.0]
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            ArrayDeclarationASTNode::class.java,
        )
    }

    @Test
    fun `string array initialization rejects mixed element types`() {
        assertSingleSemanticError(
            """
            fun main(): Unit {
                val values: String[2] = ["a", 1]
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCONSISTENT_ARRAY_TYPE,
            ArrayInitializationExpressionASTNode::class.java,
        )
    }

    @Test
    fun `array parameter index must be int`() {
        assertSingleSemanticError(
            """
            fun first(values: Int[]): Int {
                return values[true]
            }
            """.trimIndent(),
            SemanticErrorType.ARRAY_INDEX_IS_NOT_INTEGER,
            ArrayElementAccessASTNode::class.java,
        )
    }

    @Test
    fun `long array index is rejected because indexes are Int`() {
        assertSingleSemanticError(
            """
            fun first(values: Long[]): Long {
                return values[0L]
            }
            """.trimIndent(),
            SemanticErrorType.ARRAY_INDEX_IS_NOT_INTEGER,
            ArrayElementAccessASTNode::class.java,
        )
    }

    @Test
    fun `array parameter element assignment checks value type`() {
        assertSingleSemanticError(
            """
            fun write(values: Int[]): Unit {
                values[0] = "bad"
            }
            """.trimIndent(),
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
        )
    }

    @Test
    fun `global static array can be read in function body`() {
        assertNoSemanticDiagnostics(
            """
            val values: Int[2] = [1, 2]

            fun main(): Unit {
                val first: Int = values[0]
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region Template instantiation integration

    @Test
    fun `data analyzer generic helpers are valid after template instantiation`() {
        assertNoSemanticDiagnosticsAfterTemplateInstantiation(
            """
            typeclass Printable<T> {
                fun toString(value: T): String
            }

            typeclass Ordered<T> {
                fun equals(a: T, b: T): Boolean
                fun less(a: T, b: T): Boolean
            }

            instance Printable<Int> {
                fun toString(value: Int): String {
                    return intToString(value)
                }
            }

            instance Ordered<Int> {
                fun equals(a: Int, b: Int): Boolean {
                    return a == b
                }

                fun less(a: Int, b: Int): Boolean {
                    return a < b
                }
            }

            fun arrayToString<T: Printable>(arr: Array<T>, size: Int): String {
                var text: String = ""

                for (i in 0 to size) {
                    if (i == 0) {
                        text = text + toString(arr[i])
                    } else {
                        text = text + ", " + toString(arr[i])
                    }
                }

                return text
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

            fun binarySearch<T: Ordered>(arr: Array<T>, size: Int, target: T): Int {
                var left: Int = 0
                var right: Int = size - 1

                while (left <= right) {
                    var middle: Int = (left + right) / 2

                    if (equals(arr[middle], target)) {
                        return middle
                    }

                    if (less(target, arr[middle])) {
                        right = middle - 1
                    } else {
                        left = middle + 1
                    }
                }

                return -1
            }

            fun report(arr: Array<Int>, size: Int, target: Int): String {
                bubbleSort(arr, size)
                val index: Int = binarySearch(arr, size, target)
                return "index = " + intToString(index) + " data = " + arrayToString(arr, size)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `generic local static array declaration is specialized before semantic analysis`() {
        assertNoSemanticDiagnosticsAfterTemplateInstantiation(
            """
            fun firstCopy<T>(value: T): T {
                val scratch: T[1] = [value]
                return scratch[0]
            }

            fun main(): Unit {
                val result: Int = firstCopy(7)
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region Public pipeline and TODO regression coverage

    @Test
    fun `array cannot redeclare a scalar in the same block`() {
        assertSingleSemanticError(
            "fun f() { val a: Int = 1 val a: Int[1] = [2] }",
            SemanticErrorType.VARIABLE_REDECLARATION,
            ArrayDeclarationASTNode::class.java,
        )
    }

    @Test
    fun `second array declaration in the same block is rejected`() {
        assertSingleSemanticError(
            "fun f() { val a: Int[1] = [1] val a: Int[1] = [2] }",
            SemanticErrorType.VARIABLE_REDECLARATION,
            ArrayDeclarationASTNode::class.java,
        )
    }

    @Test
    fun `shadowing a parameter emits one warning for the local declaration`() {
        val warnings = analyze("fun f(x: Int) { val x: Int = 1 }").fold(
            ifLeft = { error("expected successful analysis, got $it") },
            ifRight = { it },
        )
        assertEquals(listOf(SemanticErrorType.VARIABLE_OVERSHADOW), warnings.map { it.type })
        assertInstanceOf(ScalarVariableInitializationASTNode::class.java, warnings.single().where)
    }

    @Test
    fun `global array declaration and element access share a global symbol`() {
        val analysis = analyzeAfterTemplateInstantiation("val a: Int[1] = [9] fun f(): Int { return a[0] }")
            .fold(ifLeft = { error("expected successful analysis, got $it") }, ifRight = { it })
        val symbols = analysis.resolvedSymbols.entries
        val declaration = symbols.single { it.key is DeclareGlobalVariableASTNode }.value
        val read = symbols.single { it.key is ArrayElementAccessASTNode }.value
        assertInstanceOf(SymbolId.GlobalVar::class.java, declaration)
        assertEquals(declaration, read)
    }

    @Test
    fun `for loop desugared variables are scoped and typed`() {
        assertNoSemanticDiagnostics(
            """
            fun main(): Unit {
                var total: Int = 0
                for (i in 0 to 2) {
                    total = total + i
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `raw typeclass declarations are accepted before template instantiation`() {
        assertTrue(
            parseEither(
                """
                typeclass Printable<A> {
                    fun print(a: A): Unit
                }
                """.trimIndent(),
            ).isRight(),
        )
    }

    // endregion
}
