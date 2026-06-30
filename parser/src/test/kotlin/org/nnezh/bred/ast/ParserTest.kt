package org.nnezh.bred.ast

import arrow.core.Either
import arrow.core.getOrElse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.bred.codegenerator.TemplateInstantiator
import org.nnezh.bred.common.TypeSign
import org.nnezh.bred.context.ProgramContextCollector
import org.nnezh.bred.context.ProgramGlobalContext
import org.nnezh.lexer.Lexer

class ParserTest {
    private fun parseEither(source: String): Either<ASTError, ASTNode> {
        val tokens = Lexer(source).tokenize().getOrElse { error("unexpected lexer error: $it") }
        return AbstractSyntaxTreeBuilder().build(tokens)
    }

    private fun parse(source: String): ProgramRoot =
        assertInstanceOf(ProgramRoot::class.java, parseEither(source).getOrElse { error("unexpected parser error: $it") })

    private fun parseFails(source: String): Boolean = parseEither(source).isLeft()

    @Test
    fun `empty program produces empty ProgramRoot`() {
        val root = parse("")

        assertTrue(root.functions.isEmpty())
        assertTrue(root.globalVariables.isEmpty())
        assertTrue(root.typeClasses.isEmpty())
        assertTrue(root.instances.isEmpty())
    }

    @Test
    fun `top level function gets synthetic unit return`() {
        val root = parse("fun main(): Unit { }")

        val function = root.functions.single()
        assertEquals("main", function.name)
        assertEquals(TypeSign("Unit"), function.result)
        val syntheticReturn = assertInstanceOf(ReturnFunctionStatementAstNode::class.java, function.body.statements.single())
        assertNull(syntheticReturn.expression)
        assertEquals(false, syntheticReturn.explicit)
    }

    @Test
    fun `top level generic function is parsed`() {
        val root = parse("fun prettyPrint<A: Printable>(a: A) { println(toPrettyPrinter(a)) }")

        val function = root.functions.single()
        assertEquals("prettyPrint", function.name)
        assertEquals(listOf(GenericParam("A", listOf("Printable"))), function.genericParams)
        assertEquals(FunctionArgument("a", TypeSign("A")), function.arguments.single())
    }

    @Test
    fun `global scalar and static array vals are parsed`() {
        val root = parse(
            """
            val answer: Int = 42
            val buf: Int[3] = [1, 2, 3]
            """.trimIndent()
        )

        assertEquals(2, root.globalVariables.size)
        assertEquals("answer", root.globalVariables[0].name)
        assertEquals(TypeSign("Int"), root.globalVariables[0].type)
        assertNull(root.globalVariables[0].size)
        assertEquals("buf", root.globalVariables[1].name)
        assertEquals(3, root.globalVariables[1].size)
        assertInstanceOf(ArrayInitializationExpressionASTNode::class.java, root.globalVariables[1].expression)
    }

    @Test
    fun `top level var is rejected`() {
        assertTrue(parseFails("var n: Int = 1"))
    }

    @Test
    fun `typeclass declaration is added to ProgramRoot`() {
        val root = parse("typeclass Printable<A> { fun toPrettyPrinter(a: A): String }")

        val typeClass = root.typeClasses.single()
        assertEquals("Printable", typeClass.name)
        assertEquals(GenericParam("A", emptyList<String>()), typeClass.genericParam)
        val method = typeClass.methods.single()
        assertEquals("toPrettyPrinter", method.name)
        assertEquals(TypeSign("String"), method.result)
    }

    @Test
    fun `instance declaration is added to ProgramRoot`() {
        val root = parse(
            """
            instance Printable<Int> {
                fun toPrettyPrinter(a: Int): String {
                    return intToString(a)
                }
            }
            """.trimIndent()
        )

        val instance = root.instances.single()
        assertEquals("Printable", instance.typeClassName)
        assertEquals(TypeSign("Int"), instance.type)
        assertEquals("toPrettyPrinter", instance.methods.single().name)
    }

    @Test
    fun `for statement remains desugared`() {
        val root = parse("fun main(): Unit { for (i in 0 to 2) { println(i) } }")

        val function = root.functions.single()
        val forStatement = assertInstanceOf(ForStatementAstNode::class.java, function.body.statements.first())
        assertEquals(3, forStatement.desugaredContent.statements.size)
        assertInstanceOf(WhileStatementAstNode::class.java, forStatement.desugaredContent.statements[2])
    }

    @Test
    fun `assignment lvalue must be assignable`() {
        assertTrue(parseFails("fun main(): Unit { x + 1 = 2 }"))
    }

    @Test
    fun `expression precedence is preserved`() {
        val root = parse("fun main(): Unit { val n: Int = 1 + 2 * 3 }")

        val declaration = assertInstanceOf(ScalarVariableInitializationASTNode::class.java, root.functions.single().body.statements.first())
        val plus = assertInstanceOf(BinaryExpressionASTNode::class.java, declaration.expression)
        assertEquals(BinaryOperator.Plus, plus.operator.kind)
        val star = assertInstanceOf(BinaryExpressionASTNode::class.java, plus.right)
        assertEquals(BinaryOperator.Star, star.operator.kind)
    }

    @Test
    fun `typeclass proposal sample parses valid declarations`() {
        val root = parse(
            """
            typeclass Printable<A> {
                fun toPrettyPrinter(a: A): String
            }

            instance Printable<Int> {
                fun toPrettyPrinter(a: Int): String {
                    return intToString(a)
                }
            }

            fun prettyPrint<A: Printable>(a: A) {
                println(toPrettyPrinter(a))
            }

            fun main(): Unit {
                val a: Int = 0
                prettyPrint(a)
            }
            """.trimIndent()
        )

        assertEquals(1, root.typeClasses.size)
        assertEquals(1, root.instances.size)
        assertNotNull(root.functions.single { it.name == "prettyPrint" })
        assertNotNull(root.functions.single { it.name == "main" })
    }

    @Test
    fun `program preserves declarations in their own top-level lists`() {
        val root = parse(
            """
            val answer: Int = 42
            typeclass Printable<A> { fun print(a: A) }
            instance Printable<Int> { fun print(a: Int) { println(a) } }
            fun main(): Unit { }
            """.trimIndent()
        )

        assertEquals(listOf("main"), root.functions.map { it.name })
        assertEquals(listOf("answer"), root.globalVariables.map { it.name })
        assertEquals(listOf("Printable"), root.typeClasses.map { it.name })
        assertEquals(listOf("Printable"), root.instances.map { it.typeClassName })
    }

    @Test
    fun `function can have multiple generic params and generic return type`() {
        val root = parse("fun pair<A: Printable, B>(a: A, b: B): Pair<A, B> { return makePair(a, b) }")
        val function = root.functions.single()

        assertEquals(
            listOf(GenericParam("A", listOf("Printable")), GenericParam("B", emptyList())),
            function.genericParams,
        )
        assertEquals(TypeSign("Pair", listOf(TypeSign("A"), TypeSign("B"))), function.result)
        assertEquals(listOf(FunctionArgument("a", TypeSign("A")), FunctionArgument("b", TypeSign("B"))), function.arguments)
    }

    @Test
    fun `array function argument is marked explicitly`() {
        val root = parse("fun first(values: Int[]): Int { return values[0] }")
        val argument = root.functions.single().arguments.single()

        assertEquals("values", argument.name)
        assertEquals(TypeSign("Int"), argument.type)
        assertTrue(argument.isArray)
    }

    @Test
    fun `nested generic type signs parse in arguments and instances`() {
        val root = parse(
            """
            instance Codec<List<Int>> {
                fun encode(values: List<Int>): String {
                    return stringify(values)
                }
            }
            """.trimIndent()
        )
        val instance = root.instances.single()

        assertEquals("Codec", instance.typeClassName)
        assertEquals(TypeSign("List", listOf(TypeSign("Int"))), instance.type)
        assertEquals(TypeSign("List", listOf(TypeSign("Int"))), instance.methods.single().arguments.single().type)
    }

    @Test
    fun `typeclass method without explicit result type defaults to Unit`() {
        val root = parse("typeclass Runnable<A> { fun run(a: A) }")
        val method = root.typeClasses.single().methods.single()

        assertEquals(TypeSign("Unit"), method.result)
    }

    @Test
    fun `explicit return suppresses synthetic return`() {
        val root = parse("fun main(): Unit { return Unit }")
        val statements = root.functions.single().body.statements

        assertEquals(1, statements.size)
        val returnStatement = assertInstanceOf(ReturnFunctionStatementAstNode::class.java, statements.single())
        assertTrue(returnStatement.explicit)
        assertNull(returnStatement.expression)
    }

    @Test
    fun `nested return does not suppress top-level synthetic return`() {
        val root = parse("fun main(): Unit { if (true) { return Unit } }")
        val statements = root.functions.single().body.statements

        assertInstanceOf(IfStatementAstNode::class.java, statements.first())
        val synthetic = assertInstanceOf(ReturnFunctionStatementAstNode::class.java, statements.last())
        assertFalse(synthetic.explicit)
    }

    @Test
    fun `if else while and call statements keep their AST shape`() {
        val root = parse(
            """
            fun main(): Unit {
                if (ready()) { println("yes") } else { println("no") }
                while (ready()) { tick() }
            }
            """.trimIndent()
        )
        val statements = root.functions.single().body.statements

        val ifStatement = assertInstanceOf(IfStatementAstNode::class.java, statements[0])
        assertInstanceOf(FunctionCallExpressionASTNode::class.java, ifStatement.condition)
        assertEquals(1, ifStatement.thenBlock.statements.size)
        assertEquals(1, ifStatement.elseBlock?.statements?.size)
        val whileStatement = assertInstanceOf(WhileStatementAstNode::class.java, statements[1])
        assertInstanceOf(FunctionCallExpressionASTNode::class.java, whileStatement.condition)
    }

    @Test
    fun `assignment to array element keeps index expression`() {
        val root = parse("fun main(): Unit { arr[i + 1] = value }")
        val assignment = assertInstanceOf(AssignmentStatementAstNode::class.java, root.functions.single().body.statements.first())
        val lValue = assertInstanceOf(ArrayElementAccessASTNode::class.java, assignment.lValue)

        assertEquals("arr", lValue.name)
        val index = assertInstanceOf(BinaryExpressionASTNode::class.java, lValue.index)
        assertEquals(BinaryOperator.Plus, index.operator.kind)
    }

    @Test
    fun `unary operators are right associative and bind tighter than addition`() {
        val root = parse("fun main(): Unit { val x: Int = -!flag + 1 }")
        val declaration = assertInstanceOf(ScalarVariableInitializationASTNode::class.java, root.functions.single().body.statements.first())
        val addition = assertInstanceOf(BinaryExpressionASTNode::class.java, declaration.expression)
        val unaryMinus = assertInstanceOf(UnaryExpressionASTNode::class.java, addition.left)
        val unaryNot = assertInstanceOf(UnaryExpressionASTNode::class.java, unaryMinus.operand)

        assertEquals(UnaryOperator.Minus, unaryMinus.operator.kind)
        assertEquals(UnaryOperator.Not, unaryNot.operator.kind)
    }

    @Test
    fun `function call arguments reject trailing comma`() {
        assertTrue(parseFails("fun main(): Unit { println(1,) }"))
    }

    @Test
    fun `function parameters reject trailing comma`() {
        assertTrue(parseFails("fun broken(a: Int,): Unit { }"))
    }

    @Test
    fun `generic params reject trailing comma`() {
        assertTrue(parseFails("fun broken<A,>(a: A): Unit { }"))
    }

    @Test
    fun `type sign rejects empty generic argument list`() {
        assertTrue(parseFails("fun broken(a: Box<>): Unit { }"))
    }

    @Test
    fun `typeclass requires generic parameter list`() {
        assertTrue(parseFails("typeclass Printable { fun print(a: Int) }"))
    }

    @Test
    fun `typeclass does not accept method body`() {
        assertTrue(parseFails("typeclass Printable<A> { fun print(a: A) { println(a) } }"))
    }

    @Test
    fun `instance requires exactly one type argument`() {
        assertTrue(parseFails("instance Printable { }"))
        assertTrue(parseFails("instance Printable<Int, String> { }"))
    }

    @Test
    fun `global call statement is rejected`() {
        assertTrue(parseFails("println(1)"))
    }

    @Test
    fun `else-if is rejected because else requires a block`() {
        assertTrue(parseFails("fun main(): Unit { if (true) { } else if (false) { } }"))
    }

    @Test
    fun `array declaration size must be integer literal`() {
        assertTrue(parseFails("val xs: Int[n]"))
        assertTrue(parseFails("val xs: Int[]"))
    }

    @Test
    fun `array initializer rejects scalar rhs for array declaration`() {
        assertTrue(parseFails("val xs: Int[3] = 1"))
    }

    @Test
    fun `array initializer rejects trailing and consecutive commas`() {
        assertTrue(parseFails("val xs: Int[3] = [1, 2,]"))
        assertTrue(parseFails("val xs: Int[3] = [1,, 2]"))
    }

    @Test
    fun `call suffix after array access is rejected`() {
        assertTrue(parseFails("fun main(): Unit { val x: Int = arr[0](1) }"))
    }

    @Test
    fun `indirect call statement is rejected`() {
        assertTrue(parseFails("fun main(): Unit { (f)() }"))
    }

    @Test
    fun `subtree clone replaces template type without changing original tree`() {
        val root = parse(
            """
            fun makeBox<A: Printable>(a: A): Box<A> {
                val local: A = a
                return wrap(local)
            }
            """.trimIndent()
        )
        val originalFunction = root.functions.single()

        val clone = cloneSubtreeReplacingType(root, "A", "Int", ProgramGlobalContext())
        val clonedFunction = clone.functions.single()
        val clonedLocal = assertInstanceOf(
            ScalarVariableInitializationASTNode::class.java,
            clonedFunction.body.statements.first(),
        )

        assertNotSame(root, clone)
        assertNotSame(originalFunction, clonedFunction)
        assertEquals(TypeSign("Box", listOf(TypeSign("Int"))), clonedFunction.result)
        assertEquals(listOf(FunctionArgument("a", TypeSign("Int"))), clonedFunction.arguments)
        assertEquals(TypeSign("Int"), clonedLocal.type)
        assertTrue(clonedFunction.genericParams.isEmpty())

        assertEquals(TypeSign("Box", listOf(TypeSign("A"))), originalFunction.result)
        assertEquals(listOf(FunctionArgument("a", TypeSign("A"))), originalFunction.arguments)
        assertEquals(listOf(GenericParam("A", listOf("Printable"))), originalFunction.genericParams)
    }

    @Test
    fun `program clone owns its mutable function list`() {
        val root = parse("fun id<A>(a: A): A { return a }")
        val clone = root.deepCloneReplacingType("A", "String", ProgramGlobalContext())

        clone.functions.clear()

        assertEquals(1, root.functions.size)
        assertTrue(clone.functions.isEmpty())
    }

    @Test
    fun `template instantiator mangles instance and template functions`() {
        val root = parse(
            """
            typeclass Printable<A> {
                fun toPrettyPrinter(a: A): String
            }

            instance Printable<Int> {
                fun toPrettyPrinter(value: Int): String {
                    return intToString(value)
                }
            }

            fun prettyPrint<A: Printable>(a: A) {
                println(toPrettyPrinter(a))
            }

            fun main(): Unit {
                val a: Int = 1
                prettyPrint(a)
            }
            """.trimIndent()
        )
        val instantiated = TemplateInstantiator(ProgramContextCollector().collect(root))
            .instantiate(root)
            .getOrElse { error("unexpected instantiation error: $it") }

        assertTrue(instantiated.instances.isEmpty())
        assertTrue(instantiated.typeClasses.isEmpty())
        assertNotNull(instantiated.functions.singleOrNull { it.name == "toPrettyPrinter_Int_String" })
        val prettyPrint = instantiated.functions.single { it.name == "prettyPrint_Int_Unit" }
        val printlnStatement = assertInstanceOf(CallFunctionStatementAstNode::class.java, prettyPrint.body.statements.first())
        val printlnCall = assertInstanceOf(FunctionCallExpressionASTNode::class.java, printlnStatement.expression)
        val rewrittenCall = assertInstanceOf(FunctionCallExpressionASTNode::class.java, printlnCall.arguments.single())

        assertEquals("println", printlnCall.name)
        assertEquals("toPrettyPrinter_Int_String", rewrittenCall.name)
        assertNull(instantiated.functions.singleOrNull { it.name == "prettyPrint" })
        assertEquals(1, root.instances.size)
        assertNotNull(root.functions.singleOrNull { it.name == "prettyPrint" })
    }

    @Test
    fun `template instantiator mangles unit instance method calls`() {
        val root = parse(
            """
            typeclass Print<S> {
                fun print(s: S): Unit
            }

            instance Print<String> {
                fun print(s: String) {
                    println(s)
                }
            }

            fun printValue<S: Print>(s: S) {
                print(s)
            }

            fun main(): Unit {
                val s: String = "hello"
                printValue(s)
            }
            """.trimIndent()
        )
        val instantiated = TemplateInstantiator(ProgramContextCollector().collect(root))
            .instantiate(root)
            .getOrElse { error("unexpected instantiation error: $it") }
        val printValue = instantiated.functions.single { it.name == "printValue_String_Unit" }
        val printStatement = assertInstanceOf(CallFunctionStatementAstNode::class.java, printValue.body.statements.first())
        val printCall = assertInstanceOf(FunctionCallExpressionASTNode::class.java, printStatement.expression)

        assertNotNull(instantiated.functions.singleOrNull { it.name == "print_String_Unit" })
        assertEquals("print_String_Unit", printCall.name)
        assertTrue(instantiated.instances.isEmpty())
        assertTrue(instantiated.typeClasses.isEmpty())
    }

    @Test
    fun `template instantiator returns error when constrained instance is missing`() {
        val root = parse(
            """
            typeclass Printable<A> {
                fun toPrettyPrinter(a: A): String
            }

            fun prettyPrint<A: Printable>(a: A) {
                println(toPrettyPrinter(a))
            }

            fun main(): Unit {
                val s: String = "hello"
                prettyPrint(s)
            }
            """.trimIndent()
        )

        assertTrue(TemplateInstantiator(ProgramContextCollector().collect(root)).instantiate(root).isLeft())
    }

    @Test
    fun `template instantiator returns error when instance method is missing`() {
        val root = parse(
            """
            typeclass Printable<A> {
                fun toPrettyPrinter(a: A): String
            }

            instance Printable<Int> {
                fun another(value: Int): String {
                    return intToString(value)
                }
            }

            fun prettyPrint<A: Printable>(a: A) {
                println(toPrettyPrinter(a))
            }

            fun main(): Unit {
                val a: Int = 1
                prettyPrint(a)
            }
            """.trimIndent()
        )

        assertTrue(TemplateInstantiator(ProgramContextCollector().collect(root)).instantiate(root).isLeft())
    }

    @Test
    fun `template instantiator returns error on mangled name collision`() {
        val root = parse(
            """
            fun prettyPrint<A>(a: A) {
                println(a)
            }

            fun prettyPrint_Int_Unit(a: String): Unit {
                println(a)
            }

            fun main(): Unit {
                val a: Int = 1
                prettyPrint(a)
            }
            """.trimIndent()
        )

        assertTrue(TemplateInstantiator(ProgramContextCollector().collect(root)).instantiate(root).isLeft())
    }
}
