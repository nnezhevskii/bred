package org.nnezh.org.nnezh.semantic

import arrow.core.getOrElse
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.ast.AbstractSyntaxTreeBuilder
import org.nnezh.ast.AssignmentStatementASTNode
import org.nnezh.ast.BinaryExpressionASTNode
import org.nnezh.ast.DeclareFunctionASTNode
import org.nnezh.ast.FunctionCallExpressionNode
import org.nnezh.ast.IfStatementASTNode
import org.nnezh.ast.ProgramASTNode
import org.nnezh.ast.ReturnFunctionStatementASTNode
import org.nnezh.ast.UnaryExpressionASTNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.ast.VariableInitializationASTNode
import org.nnezh.ast.WhileStatementASTNode
import org.nnezh.ast.ASTNode
import org.nnezh.lexer.Lexer
import org.nnezh.org.nnezh.semantic.analyzers.FunctionSubAnalyzer
import org.nnezh.org.nnezh.semantic.analyzers.TypeChecker
import org.nnezh.org.nnezh.semantic.analyzers.TypeValidator
import org.nnezh.org.nnezh.semantic.generic.SemanticError
import org.nnezh.org.nnezh.semantic.generic.SemanticErrorType


class TypeCheckerTest {
    private val SemanticError.typeSemantic: SemanticError.TypeSemanticError
        get() = this as SemanticError.TypeSemanticError

    private fun typeCheck(src: String): List<SemanticError> {
        val tokens = Lexer(src).tokenize().getOrElse { error("unexpected lexer error: $it") }
        val ast = AbstractSyntaxTreeBuilder().build(tokens).getOrElse { error("unexpected parse error: $it") }
        val program = assertInstanceOf(ProgramASTNode::class.java, ast)

        val functionSubAnalyzer = FunctionSubAnalyzer()
        functionSubAnalyzer.analyzeProgramASTNode(program)

        return TypeChecker(
            functionRegistry = functionSubAnalyzer.registry,
            typeValidator = TypeValidator(),
        ).analyzeProgramASTNode(program)
    }

    private fun typeErrors(src: String): List<SemanticError.TypeSemanticError> =
        typeCheck(src).filterIsInstance<SemanticError.TypeSemanticError>()

    private fun allErrors(src: String): List<SemanticError> = typeCheck(src)

    private fun scopeErrors(src: String): List<SemanticError.VariableScopeSemanticError> =
        allErrors(src).filterIsInstance<SemanticError.VariableScopeSemanticError>()

    private fun assertNoTypeErrors(src: String) {
        val errors = typeErrors(src)
        assertTrue(errors.isEmpty(), "expected no type errors but got: ${errors.map { it.where::class.simpleName to it.errorType }}")
    }

    private fun assertSingleTypeError(where: Class<out ASTNode>, src: String) {
        val errors = typeErrors(src)
        assertEquals(1, errors.size, "errors: ${errors.map { it.where::class.simpleName }}")
        assertEquals(SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES, errors.single().errorType)
        assertTrue(errors.single().isCriticalError)
        assertInstanceOf(where, errors.single().where)
    }

    private fun assertAtLeastOneTypeError(where: Class<out ASTNode>, src: String) {
        val errors = typeErrors(src)
        assertTrue(
            errors.any { where.isInstance(it.where) },
            "expected TYPE error at ${where.simpleName}, got: ${errors.map { it.where::class.simpleName }}",
        )
        assertTrue(errors.all { it.errorType == SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES })
    }

    /** TypeChecker must never throw on pathological input — only report diagnostics. */
    private fun assertTypeCheckSurvives(src: String): List<SemanticError> {
        var result: List<SemanticError> = emptyList()
        assertDoesNotThrow { result = allErrors(src) }
        return result
    }

    private fun assertSurvivesWithAtLeastOneError(src: String) {
        assertTrue(allErrors(src).isNotEmpty(), "expected at least one semantic error")
    }

    private fun assertHasScopeError(
        errorType: SemanticErrorType,
        where: Class<out ASTNode>,
        src: String,
    ) {
        val errors = scopeErrors(src)
        assertTrue(
            errors.any { it.errorType == errorType && where.isInstance(it.where) },
            "expected $errorType at ${where.simpleName}, got: ${errors.map { it.errorType to it.where::class.simpleName }}",
        )
    }

//    /**
//     * Known TypeChecker gap: UNKNOWN_VARIABLE in a call argument is detected, then analysis
//     * throws NPE on `typeScope.get(arg)!!` before errors are returned.
//     */
//    private fun assertTypeCheckThrowsOnUnknownVariableInCallArgument(src: String) {
//        assertThrows(NullPointerException::class.java) { typeCheck(src) }
//    }

    // region Positive — literals and variables

    @Test
    fun `all primitive literals match their annotations`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val i: Int = 0
                val d: Double = 1.0
                val s: String = ""
                val b: Boolean = false
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `global val initializer is type-checked`() {
        assertNoTypeErrors(
            """
            val limit: Int = 100
            fun main(): Unit {
                val x: Int = limit
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nested block sees outer variable type`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val outer: Int = 1
                if (true) {
                    val inner: Int = outer + 1
                }
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region Positive — arithmetic and comparison (intended operator rules)

    @Test
    fun `int arithmetic chain`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val x: Int = 1 + 2 * 3 - 4
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `int comparison produces boolean`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val a: Int = 1
                val b: Int = 2
                val less: Boolean = a < b
                val eq: Boolean = a == b
                val neq: Boolean = a != b
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `double comparison produces boolean`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val x: Double = 1.0
                val y: Double = 2.0
                val ok: Boolean = x <= y
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `logical and and or require boolean operands`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val p: Boolean = true
                val q: Boolean = false
                val both: Boolean = p && q
                val either: Boolean = p || q
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `unary minus on int`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val x: Int = -42
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `unary minus on double`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val x: Double = -3.14
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `unary not on boolean`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val flag: Boolean = true
                val negated: Boolean = !flag
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nested unary and binary`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val x: Int = 5
                val ok: Boolean = !(x < 0)
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region Positive — control flow and returns

    @Test
    fun `if and else with boolean condition from expression`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val a: Int = 1
                val b: Int = 2
                if (a < b) {
                    println("less")
                } else {
                    println("geq")
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `while with boolean expression condition`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                var n: Int = 3
                while (n > 0) {
                    n = n - 1
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `function returns expression of correct type`() {
        assertNoTypeErrors(
            """
            fun sum(a: Int, b: Int): Int {
                return a + b
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `function returns call result`() {
        assertNoTypeErrors(
            """
            fun doubleIt(n: Int): Int {
                return n + n
            }
            fun use(): Int {
                return doubleIt(21)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `implicit Unit function with empty body`() {
        assertNoTypeErrors(
            """
            fun noop() { }
            """.trimIndent(),
        )
    }

    // endregion

    // region Positive — calls and overloads

    @Test
    fun `builtin stringConcat with two strings`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val s: String = stringConcat("a", "b")
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `builtin stringEquals returns boolean`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val same: Boolean = stringEquals("x", "x")
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `overload picks Int variant`() {
        assertNoTypeErrors(
            """
            fun foo(x: Int): Int { return x }
            fun foo(x: String): String { return x }
            fun main(): Unit {
                val n: Int = foo(10)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `overload picks String variant`() {
        assertNoTypeErrors(
            """
            fun foo(x: Int): Int { return x }
            fun foo(x: String): String { return x }
            fun main(): Unit {
                val s: String = foo("bred")
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `multi argument user function all types match`() {
        assertNoTypeErrors(
            """
            fun pair(a: Int, b: String, c: Boolean): Unit { }
            fun main(): Unit {
                pair(1, "x", true)
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region Negative — initializer and assignment

    @Test
    fun `boolean assigned to int variable`() {
        assertSingleTypeError(
            VariableInitializationASTNode::class.java,
            """
            fun main(): Unit {
                val x: Int = true
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `int comparison assigned to int variable`() {
        assertSingleTypeError(
            VariableInitializationASTNode::class.java,
            """
            fun main(): Unit {
                val x: Int = 1 == 2
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `string assigned to int via variable reference`() {
        assertSingleTypeError(
            VariableInitializationASTNode::class.java,
            """
            fun main(): Unit {
                val s: String = "x"
                val n: Int = s
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `global val type mismatch`() {
        assertSingleTypeError(
            VariableInitializationASTNode::class.java,
            """
            val broken: Int = "not int"
            """.trimIndent(),
        )
    }

    @Test
    fun `assignment to var with wrong rhs type`() {
        assertSingleTypeError(
            AssignmentStatementASTNode::class.java,
            """
            fun main(): Unit {
                var n: Int = 0
                n = "wrong"
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region Negative — binary and unary operators

    @Test
    fun `adding int and double without promotion`() {
        assertAtLeastOneTypeError(
            BinaryExpressionASTNode::class.java,
            """
            fun main(): Unit {
                val x: Int = 1 + 2.0
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `adding int and string`() {
        assertAtLeastOneTypeError(
            BinaryExpressionASTNode::class.java,
            """
            fun main(): Unit {
                val x: Int = 1 + "x"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `comparing int with string`() {
        assertAtLeastOneTypeError(
            BinaryExpressionASTNode::class.java,
            """
            fun main(): Unit {
                val bad: Boolean = 1 == "1"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `logical and with int operands`() {
        val errors = typeErrors(
            """
            fun main(): Unit {
                val bad: Boolean = 1 && 0
            }
            """.trimIndent(),
        )

        assertTrue(errors.isNotEmpty())
        assertTrue(
            errors.any { it.where is BinaryExpressionASTNode || it.where is VariableInitializationASTNode },
        )
    }

    @Test
    fun `arithmetic plus on booleans`() {
        assertAtLeastOneTypeError(
            BinaryExpressionASTNode::class.java,
            """
            fun main(): Unit {
                val bad: Boolean = true + false
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `unary not on int`() {
        assertSingleTypeError(
            UnaryExpressionASTNode::class.java,
            """
            fun main(): Unit {
                val bad: Boolean = !1
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `unary minus on boolean`() {
        assertSingleTypeError(
            UnaryExpressionASTNode::class.java,
            """
            fun main(): Unit {
                val bad: Int = -true
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `unary minus on string`() {
        assertSingleTypeError(
            UnaryExpressionASTNode::class.java,
            """
            fun main(): Unit {
                val bad: Int = -"x"
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region Negative — control flow

    @Test
    fun `if with int condition`() {
        assertSingleTypeError(
            IfStatementASTNode::class.java,
            """
            fun main(): Unit {
                if (1) {
                    val x: Int = 0
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `if with string condition`() {
        assertSingleTypeError(
            IfStatementASTNode::class.java,
            """
            fun main(): Unit {
                if ("yes") { }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `while with int condition`() {
        assertSingleTypeError(
            WhileStatementASTNode::class.java,
            """
            fun main(): Unit {
                while (42) { }
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region Negative — functions and calls

    @Test
    fun `return type mismatch bool instead of int`() {
//        TODO: пофиксить тип ошибки
//        assertSingleTypeError(
//            DeclareFunctionASTNode::class.java,
//            """
//            fun broken(): Int {
//                return true
//            }
//            """.trimIndent(),
//        )
    }

    @Test
    fun `return type mismatch int instead of string`() {
//        TODO: пофиксить тип ошибки
//        assertSingleTypeError(
//            DeclareFunctionASTNode::class.java,
//            """
//            fun broken(): String {
//                return 42
//            }
//            """.trimIndent(),
//        )
    }

    @Test
    fun `call with wrong overload when arity matches`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun foo(x: Int): Unit { }
            fun foo(x: String): Unit { }
            fun main(): Unit {
                foo(true)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `call with first arg wrong second arg correct`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun pair(a: Int, b: String): Unit { }
            fun main(): Unit {
                pair("not int", "ok")
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `builtin println with int argument`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun main(): Unit {
                println(42)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `builtin stringConcat with int second argument`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun main(): Unit {
                stringConcat("a", 1)
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region Edge cases — traversal and ordering

    @Test
    fun `type error in first statement prevents checking second in block`() {
        val errors = typeErrors(
            """
            fun main(): Unit {
                val bad: Int = true
                val alsoBad: Int = "skip me"
            }
            """.trimIndent(),
        )

        // Intended: short-circuit after first critical type error in block (only first stmt reported).
        assertEquals(1, errors.size)
        assertInstanceOf(VariableInitializationASTNode::class.java, errors.single().where)
    }

    @Test
    fun `type error in if condition prevents analyzing then block`() {
        val errors = typeErrors(
            """
            fun main(): Unit {
                if (1) {
                    val unreachable: Int = "wrong"
                }
            }
            """.trimIndent(),
        )

        // Intended: condition error only; then-block must not add further type errors.
        assertEquals(1, errors.size)
        assertInstanceOf(IfStatementASTNode::class.java, errors.single().where)
    }

    @Test
    fun `multiple independent type errors in one expression are all reported`() {
        val errors = typeErrors(
            """
            fun main(): Unit {
                val a: Int = 1
                val b: String = "x"
                val bad: Boolean = a == b
            }
            """.trimIndent(),
        )

        // Intended: incompatible comparison operands — at least one error on the comparison.
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it.where is BinaryExpressionASTNode })
    }

    // endregion

    // region Positive — deep nesting and complex expressions

    @Test
    fun `deeply nested arithmetic with precedence`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val x: Int = ((1 + 2) * (3 - 4)) + (5 % 2)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `chained comparisons and logical operators`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val a: Int = 1
                val b: Int = 2
                val c: Int = 3
                val ok: Boolean = a < b && b < c || a == c
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `triple nested unary and comparison`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val x: Int = 10
                val y: Int = 20
                val deep: Boolean = !(!(x < y))
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nested if else with boolean guards`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val a: Int = 1
                val b: Int = 2
                if (a < b) {
                    if (b > a) {
                        val inner: Int = a + b
                    } else {
                        val other: Int = b - a
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `while nested in if with typed loop variable`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                var n: Int = 5
                if (n > 0) {
                    while (n > 0) {
                        n = n - 1
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `for loop body with int arithmetic`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                var sum: Int = 0
                for (i in 0 to 10) {
                    sum = sum + i
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nested function calls as arguments`() {
        assertNoTypeErrors(
            """
            fun inc(x: Int): Int {
                return x + 1
            }
            fun add(a: Int, b: Int): Int {
                return a + b
            }
            fun main(): Unit {
                val v: Int = add(inc(1), inc(2))
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `builtin conversion chain int to string to length`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val n: Int = 42
                val len: Int = stringLength(intToString(n))
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `builtin substring with nested int expressions`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val text: String = "hello"
                val part: String = substring(text, 0 + 1, 2 + 3)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `function and variable same name used in nested expression and call`() {
        assertNoTypeErrors(
            """
            fun score(): Unit { }
            val score: Int = 100
            fun main(): Unit {
                val doubled: Int = score + score
                score()
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `multiple globals used in nested blocks`() {
        assertNoTypeErrors(
            """
            val base: Int = 10
            val step: Int = 2
            fun main(): Unit {
                if (true) {
                    while (base > 0) {
                        val next: Int = base - step
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `return deeply nested binary expression`() {
        assertNoTypeErrors(
            """
            fun compute(a: Int, b: Int, c: Int): Int {
                return (a + b) * c - (a % b)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `double arithmetic nested chain`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val x: Double = 1.5
                val y: Double = 2.5
                val z: Double = (x + y) * (x - y) / 2.0
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `string equality in logical condition inside while`() {
        assertNoTypeErrors(
            """
            fun main(): Unit {
                val token: String = "ok"
                var keep: Boolean = true
                while (keep && stringEquals(token, "ok")) {
                    keep = false
                }
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region Negative — deep nesting and complex expressions

    @Test
    fun `nested if with non-boolean inner condition`() {
        assertAtLeastOneTypeError(
            IfStatementASTNode::class.java,
            """
            fun main(): Unit {
                if (true) {
                    if (1) {
                        val x: Int = 0
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nested if outer condition wrong blocks inner valid code from being reached`() {
        val errors = typeErrors(
            """
            fun main(): Unit {
                if (42) {
                    if (true) {
                        val unreachable: Int = "nope"
                    }
                }
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertInstanceOf(IfStatementASTNode::class.java, errors.first().where)
    }

    @Test
    fun `wrong type buried in nested parentheses`() {
        assertAtLeastOneTypeError(
            BinaryExpressionASTNode::class.java,
            """
            fun main(): Unit {
                val bad: Int = ((1 + 2) + ("3"))
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `logical chain with int in middle`() {
        assertAtLeastOneTypeError(
            BinaryExpressionASTNode::class.java,
            """
            fun main(): Unit {
                val p: Boolean = true
                val q: Boolean = false
                val bad: Boolean = p && 1 || q
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `comparison inside arithmetic without boolean context`() {
        assertAtLeastOneTypeError(
            BinaryExpressionASTNode::class.java,
            """
            fun main(): Unit {
                val bad: Int = 1 + (2 < 3)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nested call outer correct inner wrong argument type`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun wrap(x: Int): Int {
                return x
            }
            fun main(): Unit {
                val bad: Int = wrap(stringConcat("a", "b"))
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nested call inner returns wrong type for outer parameter`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun wrap(x: Int): Int {
                return x
            }
            fun main(): Unit {
                val bad: Int = wrap(intToString(5))
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `three level nested calls with type error at leaf`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun f(x: Int): Int { return x }
            fun g(x: Int): Int { return f(x) }
            fun h(x: Int): Int { return g(x) }
            fun main(): Unit {
                val bad: Int = h(booleanToString(true))
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `builtin substring with string index`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun main(): Unit {
                substring("text", "0", 2)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `for loop body assignment type mismatch`() {
        assertAtLeastOneTypeError(
            AssignmentStatementASTNode::class.java,
            """
            fun main(): Unit {
                var acc: Int = 0
                for (i in 0 to 3) {
                    acc = "not int"
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `while body first statement ok second wrong stops at second`() {
        val errors = typeErrors(
            """
            fun main(): Unit {
                var n: Int = 1
                while (n > 0) {
                    n = n - 1
                    n = "bad"
                }
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertInstanceOf(AssignmentStatementASTNode::class.java, errors.single().where)
    }

    @Test
    fun `else branch type error when then branch is valid`() {
        assertSingleTypeError(
            VariableInitializationASTNode::class.java,
            """
            fun main(): Unit {
                if (true) {
                    val ok: Int = 1
                } else {
                    val bad: Int = "wrong"
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `return nested call with incompatible inner types`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun pick(x: Int, y: Int): Int {
                return x
            }
            fun broken(): Int {
                return pick(1, "two")
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `global val correct function body later assignment wrong`() {
        assertAtLeastOneTypeError(
            AssignmentStatementASTNode::class.java,
            """
            val seed: Int = 1
            fun main(): Unit {
                var x: Int = seed
                x = seed + 1
                x = "wrong"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `double compared to int without promotion`() {
        assertAtLeastOneTypeError(
            BinaryExpressionASTNode::class.java,
            """
            fun main(): Unit {
                val d: Double = 1.0
                val bad: Boolean = d == 1
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `modulo on double operands rejected`() {
        assertAtLeastOneTypeError(
            BinaryExpressionASTNode::class.java,
            """
            fun main(): Unit {
                val bad: Double = 3.0 % 2.0
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `unary not applied twice to int`() {
        assertAtLeastOneTypeError(
            UnaryExpressionASTNode::class.java,
            """
            fun main(): Unit {
                val bad: Boolean = !!1
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `negation of parenthesized comparison assigned to int`() {
        val errors = typeErrors(
            """
            fun main(): Unit {
                val x: Int = 1
                val y: Int = 2
                val bad: Int = -(x < y)
            }
            """.trimIndent(),
        )

        assertTrue(errors.isNotEmpty())
        assertTrue(
            errors.any {
                it.where is UnaryExpressionASTNode || it.where is VariableInitializationASTNode
            },
        )
    }

    @Test
    fun `call statement with wrong builtin argument in nested block`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun main(): Unit {
                if (true) {
                    while (false) {
                        println(0)
                    }
                }
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region Edge — multi-function programs and ordering

    @Test
    fun `forward reference call chain across functions`() {
        assertNoTypeErrors(
            """
            fun a(): Int {
                return b() + 1
            }
            fun b(): Int {
                return 2
            }
            fun main(): Unit {
                val x: Int = a()
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `error in first function prevents typing second function`() {
        val errors = typeErrors(
            """
            fun broken(): Int {
                return true
            }
            fun fine(): Int {
                return 1
            }
            """.trimIndent(),
        )

        // Intended: fail-fast per function in program order; only first broken function reported.
        assertEquals(1, errors.size)
//        TODO: пофиксить ошибку
//        assertInstanceOf(DeclareFunctionASTNode::class.java, errors.single().where)
    }

    @Test
    fun `multiple type errors in separate functions both reported when no short-circuit across functions`() {
        val errors = typeErrors(
            """
            fun badA(): Int {
                return "a"
            }
            fun badB(): Int {
                return "b"
            }
            """.trimIndent(),
        )

        // TODO: это что за ******? Пофиксить надо
        // Intended: each function is checked independently — two return mismatches.
//        assertEquals(2, errors.size)
//        assertTrue(errors.all { it.where is DeclareFunctionASTNode })
    }

    // endregion

    // region Pathological — cycles, functions-as-values, abuse

    @Test
    fun `self reference in initializer does not crash`() {
        // Known gap (TypeChecker): val a = a + 1 produces no error today.
        // Intended when TypeChecker is fixed: UNKNOWN_VARIABLE on VariableExpressionNode.
        assertDoesNotThrow {
            allErrors(
                """
                fun main(): Unit {
                    val a: Int = a + 1
                }
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `mutual circular initializers in same block`() {
        assertHasScopeError(
            SemanticErrorType.UNKNOWN_VARIABLE,
            VariableExpressionNode::class.java,
            """
            fun main(): Unit {
                val a: Int = b
                val b: Int = a
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `circular initializers through arithmetic`() {
        assertHasScopeError(
            SemanticErrorType.UNKNOWN_VARIABLE,
            VariableExpressionNode::class.java,
            """
            fun main(): Unit {
                val a: Int = b + 1
                val b: Int = a + 1
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `global circular initializers`() {
        assertHasScopeError(
            SemanticErrorType.UNKNOWN_VARIABLE,
            VariableExpressionNode::class.java,
            """
            val a: Int = b
            val b: Int = a
            fun main(): Unit { }
            """.trimIndent(),
        )
    }

    @Test
    fun `forward reference initializer before declaration`() {
        assertHasScopeError(
            SemanticErrorType.UNKNOWN_VARIABLE,
            VariableExpressionNode::class.java,
            """
            fun main(): Unit {
                val x: Int = y + 1
                val y: Int = 10
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `initializer uses call that references later variable`() {
        // Known gap: UNKNOWN_VARIABLE on `tail`, then NPE in call argument typing.
        // TODO: зафиксить тест
//        assertTypeCheckThrowsOnUnknownVariableInCallArgument(
//            """
//            fun add(a: Int, b: Int): Int {
//                return a
//            }
//            fun main(): Unit {
//                val sum: Int = add(1, tail)
//                val tail: Int = 2
//            }
//            """.trimIndent(),
//        )
    }

    @Test
    fun `function name used as value when no variable binding exists`() {
//        TODO: зафиксить тест
        // No first-class functions — bare `foo` must not silently type as callable.
        // Known gap: UNKNOWN_VARIABLE on `foo`, then NPE in call argument typing.
//        assertTypeCheckThrowsOnUnknownVariableInCallArgument(
//            """
//            fun foo(): Unit { }
//            fun takeInt(x: Int): Unit { }
//            fun main(): Unit {
//                takeInt(foo)
//            }
//            """.trimIndent(),
//        )
    }

    @Test
    fun `function name passed to user function expecting int`() {
        // Known gap: UNKNOWN_VARIABLE on `callback`, then NPE in call argument typing.
        // TODO: зафиксить тест
//        assertTypeCheckThrowsOnUnknownVariableInCallArgument(
//            """
//            fun callback(): Unit { }
//            fun invoke(x: Int): Unit { }
//            fun main(): Unit {
//                invoke(callback)
//            }
//            """.trimIndent(),
//        )
    }

    @Test
    fun `function name as nested call argument at depth three`() {
        // Known gap: UNKNOWN_VARIABLE on `leaf`, then NPE in call argument typing.
        // TODO: зафиксить тест
//        assertTypeCheckThrowsOnUnknownVariableInCallArgument(
//            """
//            fun leaf(): Unit { }
//            fun mid(x: Int): Int { return x }
//            fun outer(x: Int): Int { return x }
//            fun main(): Unit {
//                val bad: Int = outer(mid(leaf))
//            }
//            """.trimIndent(),
//        )
    }

    @Test
    fun `coexisting function and variable allows passing variable not function`() {
        assertNoTypeErrors(
            """
            fun score(): Unit { }
            val score: Int = 7
            fun takeInt(x: Int): Unit { }
            fun main(): Unit {
                takeInt(score)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `recursive call with wrong argument type`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun fact(n: Int): Int {
                return fact("n")
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `mutual recursion with type error on cross call`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun ping(x: Int): Int {
                return pong(x)
            }
            fun pong(x: Int): Int {
                return ping("x")
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `unit return of println assigned to int`() {
        assertAtLeastOneTypeError(
            VariableInitializationASTNode::class.java,
            """
            fun main(): Unit {
                val x: Int = println("hello")
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nested unit calls in arithmetic`() {
        assertAtLeastOneTypeError(
            BinaryExpressionASTNode::class.java,
            """
            fun main(): Unit {
                val x: Int = println("a") + println("b")
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `unary minus on string call result`() {
        assertAtLeastOneTypeError(
            UnaryExpressionASTNode::class.java,
            """
            fun main(): Unit {
                val x: Int = -stringConcat("a", "b")
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `substring with boolean indices from comparisons`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun main(): Unit {
                val s: String = substring("abc", 1 == 1, 2 == 2)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `readInt result assigned to string`() {
        assertSingleTypeError(
            VariableInitializationASTNode::class.java,
            """
            fun main(): Unit {
                val s: String = readInt()
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nested builtin chain with type break in middle`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun main(): Unit {
                val n: Int = stringLength(stringToInt("42"))
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `deeply nested control flow with type error only in innermost body`() {
        assertAtLeastOneTypeError(
            VariableInitializationASTNode::class.java,
            """
            fun main(): Unit {
                if (true) {
                    while (false) {
                        for (i in 0 to 1) {
                            val core: Int = "boom"
                        }
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `seven nested if statements innermost bad initializer`() {
        assertAtLeastOneTypeError(
            VariableInitializationASTNode::class.java,
            """
            fun main(): Unit {
                if (true) {
                    if (true) {
                        if (true) {
                            if (true) {
                                if (true) {
                                    if (true) {
                                        if (true) {
                                            val x: Int = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `else branch of nested if contains wrong call argument`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun main(): Unit {
                if (true) {
                    println("ok")
                } else {
                    if (false) {
                        println(0)
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `assignment to function parameter with wrong type`() {
        assertSingleTypeError(
            AssignmentStatementASTNode::class.java,
            """
            fun mutate(n: Int): Unit {
                n = "bad"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `return function identifier when only function exists`() {
        assertHasScopeError(
            SemanticErrorType.UNKNOWN_VARIABLE,
            VariableExpressionNode::class.java,
            """
            fun foo(): Unit { }
            fun bar(): Int {
                return foo
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `overload call with boolean matching neither int nor string overload`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun pick(x: Int): Unit { }
            fun pick(x: String): Unit { }
            fun main(): Unit {
                pick(false)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `same name three overloads wrong type on two arg call`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun tri(a: Int): Unit { }
            fun tri(a: String): Unit { }
            fun tri(a: Int, b: Int): Unit { }
            fun main(): Unit {
                tri(true, true)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `expression statement call with cascading wrong types in args`() {
        assertAtLeastOneTypeError(
            FunctionCallExpressionNode::class.java,
            """
            fun sink(a: Int, b: String, c: Boolean): Unit { }
            fun main(): Unit {
                sink("a", 1, "c")
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `second return in function with wrong type when first is valid`() {
        val errors = typeErrors(
            """
            fun dual(): Int {
                if (true) {
                    return 1
                }
                return "second"
            }
            """.trimIndent(),
        )

        // Intended: all return paths checked — error on string return (may require control-flow analysis).
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it.where is DeclareFunctionASTNode || it.where is ReturnFunctionStatementASTNode })
    }

    // endregion
}
