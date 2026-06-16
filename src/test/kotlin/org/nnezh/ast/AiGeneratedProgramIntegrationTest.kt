package org.nnezh.org.nnezh.ast

import arrow.core.Either
import arrow.core.getOrElse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.ast.ASTNode
import org.nnezh.ast.AbstractSyntaxTreeBuilder
import org.nnezh.ast.AssignmentStatementASTNode
import org.nnezh.ast.BinaryExpressionASTNode
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.BooleanLiteralExpressionNode
import org.nnezh.ast.CallFunctionStatementASTNode
import org.nnezh.ast.DeclareFunctionASTNode
import org.nnezh.ast.DoubleLiteralExpressionNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.ForStatementASTNode
import org.nnezh.ast.FunctionCallExpressionNode
import org.nnezh.ast.IfStatementASTNode
import org.nnezh.ast.ImmutableVariableInitializationASTNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.MutableVariableInitializationASTNode
import org.nnezh.ast.ProgramASTNode
import org.nnezh.ast.ReturnFunctionStatementASTNode
import org.nnezh.ast.StatementASTNode
import org.nnezh.ast.StringLiteralExpressionNode
import org.nnezh.ast.UnaryExpressionASTNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.ast.WhileStatementASTNode
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.LexerError
import org.nnezh.lexer.Token
import org.nnezh.lexer.readSource
import org.nnezh.org.nnezh.Type

/**
 * End-to-end grammar test: reads [EXAMPLE_PATH] from disk, runs it through the
 * lexer and the AST builder, and asserts the full structure of the resulting
 * AST. Negative cases verify that lexical and syntactic errors are reported.
 *
 * Everything asserted here is grounded in docs/grammar.md; nothing relies on
 * snapshot comparison.
 */
class AiGeneratedProgramIntegrationTest {

    private companion object {
        const val EXAMPLE_PATH = "examples/ai_generated.bred"
    }

    // region Helpers

    private fun lex(src: String): Either<LexerError, List<Token>> = Lexer(src).tokenize()

    private fun build(src: String): Either<org.nnezh.org.nnezh.ast.ASTError, ASTNode> {
        val tokens = lex(src).getOrElse { error("unexpected lexer error: $it") }
        return AbstractSyntaxTreeBuilder().build(tokens)
    }

    private fun parsedProgram(): ProgramASTNode {
        val source = readSource(EXAMPLE_PATH).getOrElse { error("could not read $EXAMPLE_PATH: $it") }
        val tokens = Lexer(source).tokenize().getOrElse { error("unexpected lexer error: $it") }
        val ast = AbstractSyntaxTreeBuilder().build(tokens).getOrElse { error("unexpected parse error: $it") }
        return assertInstanceOf(ProgramASTNode::class.java, ast)
    }

    private fun varName(expression: ExpressionASTNode): String =
        assertInstanceOf(VariableExpressionNode::class.java, expression).token.lexeme

    private fun intValue(expression: ExpressionASTNode): Long =
        assertInstanceOf(IntLiteralExpressionNode::class.java, expression).value

    private fun binary(expression: ExpressionASTNode): BinaryExpressionASTNode =
        assertInstanceOf(BinaryExpressionASTNode::class.java, expression)

    private fun call(expression: ExpressionASTNode): FunctionCallExpressionNode =
        assertInstanceOf(FunctionCallExpressionNode::class.java, expression)

    private inline fun <reified T : StatementASTNode> statement(block: BlockASTNode, index: Int): T =
        assertInstanceOf(T::class.java, block.statements[index])

    // endregion

    // region Positive: file lexes and parses end-to-end

    @Test
    fun `example file lexes without errors`() {
        val source = readSource(EXAMPLE_PATH).getOrElse { error("could not read $EXAMPLE_PATH: $it") }
        assertTrue(Lexer(source).tokenize().isRight())
    }

    @Test
    fun `example file builds an AST without errors`() {
        val source = readSource(EXAMPLE_PATH).getOrElse { error("could not read $EXAMPLE_PATH: $it") }
        val tokens = Lexer(source).tokenize().getOrElse { error("unexpected lexer error: $it") }
        assertTrue(AbstractSyntaxTreeBuilder().build(tokens).isRight())
    }

    // endregion

    // region Positive: top-level structure

    @Test
    fun `root node is a program with the expected number of declarations`() {
        val program = parsedProgram()
        assertEquals(3, program.functions.size)
        assertEquals(4, program.globalVariables.size)
    }

    @Test
    fun `global variables preserve order names and types`() {
        val globals = parsedProgram().globalVariables
        assertEquals(listOf("Pi", "greeting", "flag", "limit"), globals.map { it.name })
        assertEquals(
            listOf(Type.DoubleType, Type.StringType, Type.BoolType, Type.IntType),
            globals.map { it.type },
        )
    }

    @Test
    fun `global literals carry decoded values`() {
        val globals = parsedProgram().globalVariables

        assertEquals(3.14, assertInstanceOf(DoubleLiteralExpressionNode::class.java, globals[0].value).value)
        assertEquals(
            "line1\nline2\t\"end\"\\",
            assertInstanceOf(StringLiteralExpressionNode::class.java, globals[1].value).value,
        )
        assertEquals(true, assertInstanceOf(BooleanLiteralExpressionNode::class.java, globals[2].value).value)
        assertEquals(10L, assertInstanceOf(IntLiteralExpressionNode::class.java, globals[3].value).value)
    }

    @Test
    fun `functions preserve order names and return types`() {
        val functions = parsedProgram().functions
        assertEquals(listOf("add", "compute", "noArgs"), functions.map { it.name })
        assertEquals(listOf(Type.IntType, Type.IntType, Type.UnitType), functions.map { it.resultType })
    }

    @Test
    fun `function parameters carry names and validated types`() {
        val functions = parsedProgram().functions.associateBy { it.name }

        val add = functions.getValue("add")
        assertEquals(listOf("a", "b"), add.args.arguments.map { it.name })
        assertEquals(listOf(Type.IntType, Type.IntType), add.args.arguments.map { it.type })

        assertEquals(listOf("x", "y"), functions.getValue("compute").args.arguments.map { it.name })
        assertTrue(functions.getValue("noArgs").args.arguments.isEmpty())
    }

    // endregion

    // region Positive: declarations and expression precedence

    @Test
    fun `add body declares a single immutable sum`() {
        val add = parsedProgram().functions.single { it.name == "add" }
        assertEquals(1, add.block.statements.size)

        val sum = statement<ImmutableVariableInitializationASTNode>(add.block, 0)
        assertEquals("sum", sum.name)
        assertEquals(Type.IntType, sum.type)

        val plus = binary(sum.value)
        assertInstanceOf(Token.Operator.Plus::class.java, plus.operator)
        assertEquals("a", varName(plus.left))
        assertEquals("b", varName(plus.right))
    }

    @Test
    fun `compute body has the expected statement sequence`() {
        val compute = parsedProgram().functions.single { it.name == "compute" }
        assertEquals(12, compute.block.statements.size)

        val kinds = compute.block.statements.map { it::class }
        assertEquals(ImmutableVariableInitializationASTNode::class, kinds[0])
        assertEquals(ImmutableVariableInitializationASTNode::class, kinds[6])
        assertEquals(AssignmentStatementASTNode::class, kinds[7])
        assertEquals(CallFunctionStatementASTNode::class, kinds[8])
        assertEquals(IfStatementASTNode::class, kinds[9])
        assertEquals(WhileStatementASTNode::class, kinds[10])
        assertEquals(ForStatementASTNode::class, kinds[11])
    }

    @Test
    fun `additive and multiplicative precedence builds left-leaning tree`() {
        // val a: Int = x + y * 2 - 1  =>  ((x + (y * 2)) - 1)
        val compute = parsedProgram().functions.single { it.name == "compute" }
        val a = statement<ImmutableVariableInitializationASTNode>(compute.block, 0)

        val minus = binary(a.value)
        assertInstanceOf(Token.Operator.Minus::class.java, minus.operator)
        assertEquals(1L, intValue(minus.right))

        val plus = binary(minus.left)
        assertInstanceOf(Token.Operator.Plus::class.java, plus.operator)
        assertEquals("x", varName(plus.left))

        val times = binary(plus.right)
        assertInstanceOf(Token.Operator.Star::class.java, times.operator)
        assertEquals("y", varName(times.left))
        assertEquals(2L, intValue(times.right))
    }

    @Test
    fun `parentheses override precedence`() {
        // val b: Int = (x + y) * 2  =>  ((x + y) * 2)
        val compute = parsedProgram().functions.single { it.name == "compute" }
        val b = statement<ImmutableVariableInitializationASTNode>(compute.block, 1)

        val times = binary(b.value)
        assertInstanceOf(Token.Operator.Star::class.java, times.operator)
        assertEquals(2L, intValue(times.right))

        val plus = binary(times.left)
        assertInstanceOf(Token.Operator.Plus::class.java, plus.operator)
        assertEquals("x", varName(plus.left))
        assertEquals("y", varName(plus.right))
    }

    @Test
    fun `logical precedence binds and tighter than or`() {
        // val c: Boolean = x < y && y > 0 || !flag  =>  (((x < y) && (y > 0)) || (!flag))
        val compute = parsedProgram().functions.single { it.name == "compute" }
        val c = statement<ImmutableVariableInitializationASTNode>(compute.block, 2)

        val or = binary(c.value)
        assertInstanceOf(Token.Operator.Or::class.java, or.operator)

        val not = assertInstanceOf(UnaryExpressionASTNode::class.java, or.right)
        assertInstanceOf(Token.Operator.Not::class.java, not.operator)
        assertEquals("flag", varName(not.operand))

        val and = binary(or.left)
        assertInstanceOf(Token.Operator.And::class.java, and.operator)

        val lt = binary(and.left)
        assertInstanceOf(Token.Operator.Lt::class.java, lt.operator)
        assertEquals("x", varName(lt.left))
        assertEquals("y", varName(lt.right))

        val gt = binary(and.right)
        assertInstanceOf(Token.Operator.Gt::class.java, gt.operator)
        assertEquals("y", varName(gt.left))
        assertEquals(0L, intValue(gt.right))
    }

    @Test
    fun `equality and remainder operators are recognized`() {
        val compute = parsedProgram().functions.single { it.name == "compute" }

        val d = statement<ImmutableVariableInitializationASTNode>(compute.block, 3)
        val eq = binary(d.value)
        assertInstanceOf(Token.Operator.Eq::class.java, eq.operator)
        assertEquals("x", varName(eq.left))
        assertEquals("y", varName(eq.right))

        val g = statement<ImmutableVariableInitializationASTNode>(compute.block, 6)
        val rem = binary(g.value)
        assertInstanceOf(Token.Operator.Percent::class.java, rem.operator)
        assertEquals("x", varName(rem.left))
        assertEquals(3L, intValue(rem.right))
    }

    @Test
    fun `unary minus binds tighter than addition`() {
        // val e: Int = -x + 1  =>  ((-x) + 1)
        val compute = parsedProgram().functions.single { it.name == "compute" }
        val e = statement<ImmutableVariableInitializationASTNode>(compute.block, 4)

        val plus = binary(e.value)
        assertInstanceOf(Token.Operator.Plus::class.java, plus.operator)
        assertEquals(1L, intValue(plus.right))

        val negation = assertInstanceOf(UnaryExpressionASTNode::class.java, plus.left)
        assertInstanceOf(Token.Operator.Minus::class.java, negation.operator)
        assertEquals("x", varName(negation.operand))
    }

    @Test
    fun `double literal participates in multiplication`() {
        // val f: Double = 2.0 * Pi
        val compute = parsedProgram().functions.single { it.name == "compute" }
        val f = statement<ImmutableVariableInitializationASTNode>(compute.block, 5)
        assertEquals(Type.DoubleType, f.type)

        val times = binary(f.value)
        assertInstanceOf(Token.Operator.Star::class.java, times.operator)
        assertEquals(2.0, assertInstanceOf(DoubleLiteralExpressionNode::class.java, times.left).value)
        assertEquals("Pi", varName(times.right))
    }

    // endregion

    // region Positive: assignments and calls

    @Test
    fun `assignment statement targets an identifier and parses its call value`() {
        // total = add(x, y)
        val compute = parsedProgram().functions.single { it.name == "compute" }
        val assignment = statement<AssignmentStatementASTNode>(compute.block, 7)
        assertEquals("total", assignment.name)

        val addCall = call(assignment.value)
        assertEquals("add", addCall.name.lexeme)
        assertEquals(listOf("x", "y"), addCall.arguments.map { varName(it) })
    }

    @Test
    fun `call statement supports nested function calls`() {
        // println(a, b, max(x, min(y, 1)))
        val compute = parsedProgram().functions.single { it.name == "compute" }
        val callStatement = statement<CallFunctionStatementASTNode>(compute.block, 8)

        val println = call(callStatement.expression)
        assertEquals("println", println.name.lexeme)
        assertEquals(3, println.arguments.size)
        assertEquals("a", varName(println.arguments[0]))
        assertEquals("b", varName(println.arguments[1]))

        val max = call(println.arguments[2])
        assertEquals("max", max.name.lexeme)
        assertEquals("x", varName(max.arguments[0]))

        val min = call(max.arguments[1])
        assertEquals("min", min.name.lexeme)
        assertEquals("y", varName(min.arguments[0]))
        assertEquals(1L, intValue(min.arguments[1]))
    }

    // endregion

    // region Positive: control flow

    @Test
    fun `if statement has condition then-block and else-block`() {
        val compute = parsedProgram().functions.single { it.name == "compute" }
        val ifStatement = statement<IfStatementASTNode>(compute.block, 9)

        val condition = binary(ifStatement.condition)
        assertInstanceOf(Token.Operator.Gt::class.java, condition.operator)
        assertEquals("x", varName(condition.left))
        assertEquals("y", varName(condition.right))

        val thenAssign = assertInstanceOf(
            AssignmentStatementASTNode::class.java,
            ifStatement.thenBlock.statements.single(),
        )
        assertEquals("total", thenAssign.name)
        assertEquals("x", varName(thenAssign.value))

        val elseBlock = ifStatement.elseBlock.leftOrNull()
            ?: error("expected an else block")
        val elseAssign = assertInstanceOf(
            AssignmentStatementASTNode::class.java,
            elseBlock.statements.single(),
        )
        assertEquals("total", elseAssign.name)
        assertEquals("y", varName(elseAssign.value))
    }

    @Test
    fun `while statement keeps its condition and body`() {
        val compute = parsedProgram().functions.single { it.name == "compute" }
        val whileStatement = statement<WhileStatementASTNode>(compute.block, 10)

        val condition = binary(whileStatement.condition)
        assertInstanceOf(Token.Operator.Lt::class.java, condition.operator)
        assertEquals("x", varName(condition.left))
        assertEquals("limit", varName(condition.right))

        assertEquals(2, whileStatement.bodyBlock.statements.size)
        val increment = assertInstanceOf(
            AssignmentStatementASTNode::class.java,
            whileStatement.bodyBlock.statements[0],
        )
        assertEquals("x", increment.name)
        val stepCall = assertInstanceOf(
            CallFunctionStatementASTNode::class.java,
            whileStatement.bodyBlock.statements[1],
        )
        assertEquals("step", call(stepCall.expression).name.lexeme)
        assertTrue(call(stepCall.expression).arguments.isEmpty())
    }

    @Test
    fun `for statement is desugared into a counter and a while loop`() {
        // for (i in 0 to limit) { println(i) }
        val compute = parsedProgram().functions.single { it.name == "compute" }
        val forStatement = statement<ForStatementASTNode>(compute.block, 11)

        val desugared = forStatement.desugaredContent.statements
        assertEquals(2, desugared.size)

        val counter = assertInstanceOf(MutableVariableInitializationASTNode::class.java, desugared[0])
        assertEquals("i", counter.name)
        assertEquals(Type.IntType, counter.type)
        assertEquals(0L, intValue(counter.value))

        val loop = assertInstanceOf(WhileStatementASTNode::class.java, desugared[1])
        val condition = binary(loop.condition)
        assertInstanceOf(Token.Operator.Le::class.java, condition.operator)
        assertEquals("i", varName(condition.left))
        assertEquals("limit", varName(condition.right))

        // user body statements come first, then the synthesized increment.
        assertEquals(2, loop.bodyBlock.statements.size)
        val body = assertInstanceOf(CallFunctionStatementASTNode::class.java, loop.bodyBlock.statements[0])
        assertEquals("println", call(body.expression).name.lexeme)
        assertEquals("i", varName(call(body.expression).arguments.single()))

        val increment = assertInstanceOf(AssignmentStatementASTNode::class.java, loop.bodyBlock.statements[1])
        assertEquals("i", increment.name)
        val incExpr = binary(increment.value)
        assertInstanceOf(Token.Operator.Plus::class.java, incExpr.operator)
        assertEquals("i", varName(incExpr.left))
        assertEquals(1L, intValue(incExpr.right))
    }

    @Test
    fun `function with empty parameter list has an empty body`() {
        val noArgs = parsedProgram().functions.single { it.name == "noArgs" }
        assertTrue(noArgs.args.arguments.isEmpty())
        assertTrue(noArgs.block.statements.isEmpty())
    }

    @Test
    fun `function with return expression parses end-to-end`() {
        val ast = build("fun f(): Int { return 1 }").getOrElse { error("unexpected parse error: $it") }
        val function = assertInstanceOf(ProgramASTNode::class.java, ast).functions.single()
        val returnStmt = assertInstanceOf(ReturnFunctionStatementASTNode::class.java, function.block.statements.single())
        assertTrue(returnStmt.expression.isRight())
        assertEquals(1L, (returnStmt.expression.getOrNull() as IntLiteralExpressionNode).value)
    }

    @Test
    fun `function with bare return parses as Unit end-to-end`() {
        val ast = build("fun main() { return }").getOrElse { error("unexpected parse error: $it") }
        val function = assertInstanceOf(ProgramASTNode::class.java, ast).functions.single()
        val returnStmt = assertInstanceOf(ReturnFunctionStatementASTNode::class.java, function.block.statements.single())
        assertTrue(returnStmt.expression.isLeft())
        assertEquals(Type.UnitType, returnStmt.expression.leftOrNull())
    }

    @Test
    fun `function with return Unit parses end-to-end`() {
        val ast = build("fun main() { return Unit }").getOrElse { error("unexpected parse error: $it") }
        val function = assertInstanceOf(ProgramASTNode::class.java, ast).functions.single()
        val returnStmt = assertInstanceOf(ReturnFunctionStatementASTNode::class.java, function.block.statements.single())
        assertTrue(returnStmt.expression.isLeft())
        assertEquals(Type.UnitType, returnStmt.expression.leftOrNull())
    }

    @Test
    fun `max function with conditional returns parses end-to-end`() {
        val src = """
            fun max(a: Int, b: Int): Int {
                if (a > b) {
                    return a
                }
                return b
            }
        """.trimIndent()
        val ast = build(src).getOrElse { error("unexpected parse error: $it") }
        val max = assertInstanceOf(ProgramASTNode::class.java, ast).functions.single()
        assertEquals("max", max.name)
        assertEquals(Type.IntType, max.resultType)
        assertEquals(2, max.block.statements.size)
        assertInstanceOf(IfStatementASTNode::class.java, max.block.statements[0])
        assertInstanceOf(ReturnFunctionStatementASTNode::class.java, max.block.statements[1])
    }

    // endregion

    // region Negative: lexical errors

    @Test
    fun `unterminated string is a lexical error`() {
        val error = lex("val s: String = \"abc").leftOrNull()
        assertInstanceOf(LexerError.UnterminatedString::class.java, error)
    }

    @Test
    fun `unknown character is a lexical error`() {
        val error = lex("@").leftOrNull()
        assertInstanceOf(LexerError.UnexpectedCharacter::class.java, error)
    }

    @Test
    fun `unterminated block comment is a lexical error`() {
        val error = lex("/* never closed").leftOrNull()
        assertInstanceOf(LexerError.UnterminatedBlockComment::class.java, error)
    }

    @Test
    fun `unknown escape sequence is a lexical error`() {
        val error = lex("\"\\q\"").leftOrNull()
        assertInstanceOf(LexerError.UnknownEscape::class.java, error)
    }

    @Test
    fun `number glued to letters is a lexical error`() {
        val error = lex("12abc").leftOrNull()
        assertInstanceOf(LexerError.InvalidNumber::class.java, error)
    }

    @Test
    fun `lone ampersand is a lexical error`() {
        val error = lex("&").leftOrNull()
        assertInstanceOf(LexerError.UnexpectedCharacter::class.java, error)
    }

    // endregion

    // region Negative: syntactic errors

    @Test
    fun `unclosed block fails to parse`() {
        assertTrue(build("fun f(): Unit {").isLeft())
    }

    @Test
    fun `unclosed parenthesis fails to parse`() {
        assertTrue(build("val x: Int = (1 + 2").isLeft())
    }

    @Test
    fun `missing expression after assign fails to parse`() {
        val message = build("val x: Int =").leftOrNull()?.message
        assertTrue(message?.contains("Expected expression") == true, "was: $message")
    }

    @Test
    fun `function without explicit return type defaults to Unit`() {
        val ast = build("fun main() { }").getOrElse { error("unexpected parse error: $it") }
        val program = assertInstanceOf(ProgramASTNode::class.java, ast)
        val main = program.functions.single()
        assertEquals("main", main.name)
        assertEquals(Type.UnitType, main.resultType)
    }

    @Test
    fun `val without type annotation fails to parse`() {
        val message = build("val x = 1").leftOrNull()?.message
        assertTrue(message?.contains("Expected :") == true, "was: $message")
    }

    @Test
    fun `unknown function return type fails to parse`() {
        val message = build("fun f(): Foo { }").leftOrNull()?.message
        assertTrue(message?.contains("Unexpected type Foo") == true, "was: $message")
    }

    @Test
    fun `unknown declaration type fails to parse`() {
        val message = build("val x: Foo = 1").leftOrNull()?.message
        assertTrue(message?.contains("Invalid type Foo") == true, "was: $message")
    }

    @Test
    fun `trailing comma in call arguments fails to parse`() {
        assertTrue(build("fun f(): Unit { g(1,) }").isLeft())
    }

    @Test
    fun `top-level statement fails to parse`() {
        val message = build("x = 1").leftOrNull()?.message
        assertTrue(message?.contains("Expected function or constant declaration") == true, "was: $message")
    }

    @Test
    fun `dangling else at top level fails to parse`() {
        val message = build("else { }").leftOrNull()?.message
        assertTrue(message?.contains("Expected function or constant declaration") == true, "was: $message")
    }

    // endregion
}
