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
import org.nnezh.bred.common.TypeRef
import org.nnezh.bred.context.ProgramContextCollector
import org.nnezh.bred.context.ProgramGlobalContext
import org.nnezh.bred.context.ProgramTypeResolver
import org.nnezh.lexer.Lexer

class ParserTest {
    private fun parseEither(source: String): Either<ASTError, ASTNode> {
        val tokens = Lexer(source).tokenize().getOrElse { error("unexpected lexer error: $it") }
        return AbstractSyntaxTreeBuilder().build(tokens)
    }

    private fun parse(source: String): ProgramRoot =
        assertInstanceOf(ProgramRoot::class.java, parseEither(source).getOrElse { error("unexpected parser error: $it") })

    private fun parseFails(source: String): Boolean = parseEither(source).isLeft()

    private fun resolve(root: ProgramRoot): ProgramRoot {
        val initialContext = ProgramContextCollector().collect(root)
        return ProgramTypeResolver(initialContext)
            .resolve(root)
            .getOrElse { error("unexpected type resolution error: $it") }
    }

    private fun instantiate(root: ProgramRoot): ProgramRoot {
        val resolved = resolve(root)
        val context = ProgramContextCollector().collect(resolved)
        return TemplateInstantiator(context)
            .instantiate(resolved)
            .getOrElse { error("unexpected instantiation error: $it") }
    }

    private fun instantiateEither(root: ProgramRoot): Either<ASTError, ProgramRoot> {
        val resolved = resolve(root)
        val context = ProgramContextCollector().collect(resolved)
        return TemplateInstantiator(context).instantiate(resolved)
    }

    private fun FieldAccessChainExpressionASTNode.fieldNames(): List<String> =
        buildList {
            var current: FieldAccessChainExpressionASTNode? = this@fieldNames
            while (current != null) {
                add(current.root)
                current = current.next
            }
        }

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
        assertEquals(TypeRef("Unit"), function.result)
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
        assertEquals(FunctionArgument("a", TypeRef("A")), function.arguments.single())
    }

    @Test
    fun `generic parameter can have multiple constraints`() {
        val root = parse("fun f<T: Ordered + Printable>(x: T) { }")

        val function = root.functions.single()
        assertEquals(listOf(GenericParam("T", listOf("Ordered", "Printable"))), function.genericParams)
        assertEquals(FunctionArgument("x", TypeRef("T")), function.arguments.single())
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
        assertEquals(TypeRef("Int"), root.globalVariables[0].type)
        assertNull(root.globalVariables[0].size)
        assertEquals("buf", root.globalVariables[1].name)
        assertEquals(3, root.globalVariables[1].size)
        assertInstanceOf(ArrayInitializationExpressionASTNode::class.java, root.globalVariables[1].expression)
    }

    @Test
    fun `global static array accepts explicit Array type form`() {
        val root = parse("val values: Array<Int>[2] = [1, 2]")

        val values = root.globalVariables.single()
        assertEquals("values", values.name)
        assertEquals(TypeRef("Array", listOf(TypeRef("Int"))), values.type)
        assertEquals(2, values.size)
        assertInstanceOf(ArrayInitializationExpressionASTNode::class.java, values.expression)
    }

    @Test
    fun `product type declaration is parsed`() {
        val root = parse("type Person(name: String, age: Int)")

        val person = assertInstanceOf(DeclareProductTypeASTNode::class.java, root.types.single())
        assertEquals("Person", person.name)
        assertFalse(person.isMutable)
        assertEquals(linkedMapOf("name" to TypeRef("String"), "age" to TypeRef("Int")), person.fields)
    }

    @Test
    fun `mutable product type declaration is parsed`() {
        val root = parse("type mut Counter(value: Int)")

        val counter = assertInstanceOf(DeclareProductTypeASTNode::class.java, root.types.single())
        assertEquals("Counter", counter.name)
        assertTrue(counter.isMutable)
        assertEquals(linkedMapOf("value" to TypeRef("Int")), counter.fields)
    }

    @Test
    fun `product field accepts generic type sign`() {
        val root = parse("type Box(value: Array<Int>)")

        val box = assertInstanceOf(DeclareProductTypeASTNode::class.java, root.types.single())
        assertEquals(linkedMapOf("value" to TypeRef("Array", listOf(TypeRef("Int")))), box.fields)
    }

    @Test
    fun `sum type declaration with generic variant argument is parsed`() {
        val root = parse("type MaybeInt = Nothing | Just<Int>")

        val maybe = assertInstanceOf(DeclareSumTypeASTNode::class.java, root.types.single())
        assertEquals("MaybeInt", maybe.name)
        assertTrue(maybe.genericParams.isEmpty())
        assertEquals(listOf("Nothing", "Just"), maybe.variants.map { it.name })
        assertTrue(maybe.variants[0].typeArguments.isEmpty())
        assertTrue(maybe.variants[0].fields.isEmpty())
        assertEquals(listOf(TypeRef("Int")), maybe.variants[1].typeArguments)
        assertTrue(maybe.variants[1].fields.isEmpty())
    }

    @Test
    fun `generic sum type declaration with constructor fields is parsed`() {
        val root = parse("type Maybe<T> = Nothing | Just<T>(a: T)")

        val maybe = assertInstanceOf(DeclareSumTypeASTNode::class.java, root.types.single())
        assertEquals("Maybe", maybe.name)
        assertEquals(listOf(GenericParam("T", emptyList())), maybe.genericParams)

        val just = maybe.variants[1]
        assertEquals("Just", just.name)
        assertEquals(listOf(TypeRef("T")), just.typeArguments)
        assertEquals(listOf(SumTypeVariantFieldASTNode("a", TypeRef("T"))), just.fields)
    }

    @Test
    fun `generic sum type constructor call is parsed`() {
        val root = parse(
            """
            type Maybe<T> = Just<T>(v: T) | Nothing

            fun main() {
                val x: Maybe<Int> = Just<Int>(1)
            }
            """.trimIndent()
        )

        val declaration = assertInstanceOf(
            ScalarVariableInitializationASTNode::class.java,
            root.functions.single().body.statements.first(),
        )
        val call = assertInstanceOf(FunctionCallExpressionASTNode::class.java, declaration.expression)

        assertEquals(TypeRef("Maybe", listOf(TypeRef("Int"))), declaration.type)
        assertEquals("Just", call.name)
        assertEquals(listOf(TypeRef("Int")), call.typeArguments)
        assertEquals(1, call.arguments.size)
    }

    @Test
    fun `enum-like and payload sum type declarations are parsed`() {
        val root = parse(
            """
            type Color = RED | GREEN | BLUE
            type Response = NOT_FOUND | SUCCESS(value: Int) | FAIL(reason: String, code: Int) | UNATHORIZED
            """.trimIndent()
        )

        val color = assertInstanceOf(DeclareSumTypeASTNode::class.java, root.types[0])
        val response = assertInstanceOf(DeclareSumTypeASTNode::class.java, root.types[1])

        assertEquals(listOf("RED", "GREEN", "BLUE"), color.variants.map { it.name })
        assertTrue(color.variants.all { it.typeArguments.isEmpty() && it.fields.isEmpty() })
        assertEquals(listOf("NOT_FOUND", "SUCCESS", "FAIL", "UNATHORIZED"), response.variants.map { it.name })
        assertEquals(listOf(SumTypeVariantFieldASTNode("value", TypeRef("Int"))), response.variants[1].fields)
        assertEquals(
            listOf(
                SumTypeVariantFieldASTNode("reason", TypeRef("String")),
                SumTypeVariantFieldASTNode("code", TypeRef("Int")),
            ),
            response.variants[2].fields,
        )
    }

    @Test
    fun `constructor call and field access chain are parsed`() {
        val root = parse(
            """
            type Address(city: String)
            type Person(name: String, address: Address)
            fun main(): Unit {
                val person: Person = Person("Nika", Address("Berlin"))
                val city: String = person.address.city
                person.address.city = "Paris"
            }
            """.trimIndent()
        )

        val statements = root.functions.single().body.statements
        val person = assertInstanceOf(ScalarVariableInitializationASTNode::class.java, statements[0])
        val personConstructor = assertInstanceOf(FunctionCallExpressionASTNode::class.java, person.expression)
        val addressConstructor = assertInstanceOf(FunctionCallExpressionASTNode::class.java, personConstructor.arguments[1])
        val city = assertInstanceOf(ScalarVariableInitializationASTNode::class.java, statements[1])
        val cityAccess = assertInstanceOf(FieldAccessChainExpressionASTNode::class.java, city.expression)
        val assignment = assertInstanceOf(AssignmentStatementAstNode::class.java, statements[2])
        val assignmentAccess = assertInstanceOf(FieldAccessChainExpressionASTNode::class.java, assignment.lValue)

        assertEquals("Person", personConstructor.name)
        assertEquals("Address", addressConstructor.name)
        assertEquals(listOf("person", "address", "city"), cityAccess.fieldNames())
        assertEquals(listOf("person", "address", "city"), assignmentAccess.fieldNames())
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
        assertEquals(TypeRef("String"), method.result)
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
        assertEquals(TypeRef("Int"), instance.type)
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
    fun `when statement parses expression cases and else branch`() {
        val root = parse(
            """
            fun main(): Unit {
                when (value) {
                    1 -> { println("one") }
                    value + 1 -> { value = value + 1 }
                    else -> { println("other") }
                }
            }
            """.trimIndent()
        )

        val statement = assertInstanceOf(WhenStatementAstNode::class.java, root.functions.single().body.statements.first())
        val expression = statement.expression

        assertEquals("value", assertInstanceOf(VariableExpressionASTNode::class.java, expression.subject).token.lexeme)
        assertEquals(3, expression.branches.size)
        assertEquals(1, assertInstanceOf(IntLiteralExpressionASTNode::class.java, expression.branches[0].condition).value)
        assertInstanceOf(BinaryExpressionASTNode::class.java, expression.branches[1].condition)
        assertNull(expression.branches[2].condition)
        assertInstanceOf(CallFunctionStatementAstNode::class.java, expression.branches[0].bodyBlock.statements.single())
        assertInstanceOf(AssignmentStatementAstNode::class.java, expression.branches[1].bodyBlock.statements.single())
        assertInstanceOf(CallFunctionStatementAstNode::class.java, expression.branches[2].bodyBlock.statements.single())
    }

    @Test
    fun `when expression parses in initializer`() {
        val root = parse(
            """
            fun choose(value: Int): Int {
                val result: Int = when (value) {
                    0 -> { return 1 }
                    else -> { return value }
                }
            }
            """.trimIndent()
        )

        val declaration = assertInstanceOf(
            ScalarVariableInitializationASTNode::class.java,
            root.functions.single().body.statements.first(),
        )
        val expression = assertInstanceOf(WhenExpressionASTNode::class.java, declaration.expression)

        assertEquals("value", assertInstanceOf(VariableExpressionASTNode::class.java, expression.subject).token.lexeme)
        assertEquals(2, expression.branches.size)
        assertEquals(0, assertInstanceOf(IntLiteralExpressionASTNode::class.java, expression.branches[0].condition).value)
        assertNull(expression.branches[1].condition)
        assertInstanceOf(ReturnFunctionStatementAstNode::class.java, expression.branches[0].bodyBlock.statements.single())
        assertInstanceOf(ReturnFunctionStatementAstNode::class.java, expression.branches[1].bodyBlock.statements.single())
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
    fun `long local initializer keeps TypeRef and Long literal node`() {
        val root = parse("fun main(): Unit { val n: Long = 42L }")

        val declaration = assertInstanceOf(ScalarVariableInitializationASTNode::class.java, root.functions.single().body.statements.first())
        val literal = assertInstanceOf(LongLiteralExpressionASTNode::class.java, declaration.expression)

        assertEquals(TypeRef("Long"), declaration.type)
        assertEquals(42L, literal.value)
    }

    @Test
    fun `parentheses override arithmetic precedence`() {
        val root = parse("fun main(): Unit { val n: Int = (1 + 2) * 3 }")

        val declaration = assertInstanceOf(ScalarVariableInitializationASTNode::class.java, root.functions.single().body.statements.first())
        val multiply = assertInstanceOf(BinaryExpressionASTNode::class.java, declaration.expression)
        val groupedAddition = assertInstanceOf(BinaryExpressionASTNode::class.java, multiply.left)

        assertEquals(BinaryOperator.Star, multiply.operator.kind)
        assertEquals(BinaryOperator.Plus, groupedAddition.operator.kind)
    }

    @Test
    fun `binary operators at same precedence are left associative`() {
        val root = parse(
            """
            fun main(): Unit {
                val arithmetic: Int = 1 - 2 - 3
                val logical: Boolean = a || b || c
            }
            """.trimIndent()
        )
        val declarations = root.functions.single().body.statements.filterIsInstance<ScalarVariableInitializationASTNode>()

        val outerMinus = assertInstanceOf(BinaryExpressionASTNode::class.java, declarations[0].expression)
        val innerMinus = assertInstanceOf(BinaryExpressionASTNode::class.java, outerMinus.left)
        assertEquals(BinaryOperator.Minus, outerMinus.operator.kind)
        assertEquals(BinaryOperator.Minus, innerMinus.operator.kind)

        val outerOr = assertInstanceOf(BinaryExpressionASTNode::class.java, declarations[1].expression)
        val innerOr = assertInstanceOf(BinaryExpressionASTNode::class.java, outerOr.left)
        assertEquals(BinaryOperator.Or, outerOr.operator.kind)
        assertEquals(BinaryOperator.Or, innerOr.operator.kind)
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
    fun `data analyzer entrypoint parses globals typeclass instance arrays and report call`() {
        val root = parse(
            """
            val ProgramName: String = "Bred data analyzer"
            val DiagnosticsEnabled: Boolean = true

            typeclass Printable<A> {
                fun toString(value: A): String
            }

            instance Printable<Int> {
                fun toString(value: Int): String {
                    return intToString(value)
                }
            }

            fun main(): Unit {
                var requestedSize: Int = readInt()
                var seed: Int = readInt()
                var target: Int = readInt()
                var threshold: Int = readInt()

                val raw: Array<Int>[64]
                val sorted: Array<Int>[64]

                println("size = " + intToString(requestedSize))
            }
            """.trimIndent()
        )

        assertEquals(listOf("ProgramName", "DiagnosticsEnabled"), root.globalVariables.map { it.name })
        assertEquals(listOf("Printable"), root.typeClasses.map { it.name })
        assertEquals(listOf("Printable"), root.instances.map { it.typeClassName })

        val main = root.functions.single { it.name == "main" }
        val declarations = main.body.statements.filterIsInstance<ScalarVariableInitializationASTNode>()
        assertEquals(listOf("requestedSize", "seed", "target", "threshold"), declarations.map { it.name })
        assertEquals(listOf(TypeRef("Int"), TypeRef("Int"), TypeRef("Int"), TypeRef("Int")), declarations.map { it.type })

        val arrays = main.body.statements.filterIsInstance<ArrayDeclarationASTNode>()
        assertEquals(listOf("raw", "sorted"), arrays.map { it.name })
        assertEquals(listOf(64, 64), arrays.map { it.size })
        assertEquals(
            listOf(TypeRef("Array", listOf(TypeRef("Int"))), TypeRef("Array", listOf(TypeRef("Int")))),
            arrays.map { it.type },
        )

        val printStatement = assertInstanceOf(CallFunctionStatementAstNode::class.java, main.body.statements[6])
        val printCall = assertInstanceOf(FunctionCallExpressionASTNode::class.java, printStatement.expression)
        assertEquals("println", printCall.name)
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
        assertEquals(TypeRef("Pair", listOf(TypeRef("A"), TypeRef("B"))), function.result)
        assertEquals(listOf(FunctionArgument("a", TypeRef("A")), FunctionArgument("b", TypeRef("B"))), function.arguments)
    }

    @Test
    fun `array function argument is marked explicitly`() {
        val root = parse("fun first(values: Int[]): Int { return values[0] }")
        val argument = root.functions.single().arguments.single()

        assertEquals("values", argument.name)
        assertEquals(TypeRef("Int"), argument.type)
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
        assertEquals(TypeRef("List", listOf(TypeRef("Int"))), instance.type)
        assertEquals(TypeRef("List", listOf(TypeRef("Int"))), instance.methods.single().arguments.single().type)
    }

    @Test
    fun `typeclass method without explicit result type defaults to Unit`() {
        val root = parse("typeclass Runnable<A> { fun run(a: A) }")
        val method = root.typeClasses.single().methods.single()

        assertEquals(TypeRef("Unit"), method.result)
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
    fun `bare return before closing brace is explicit unit and suppresses synthetic return`() {
        val root = parse("fun main(): Unit { return }")
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
    fun `generic constraints reject malformed plus lists`() {
        assertTrue(parseFails("fun broken<T: Ordered +>(a: T): Unit { }"))
        assertTrue(parseFails("fun broken<T: + Ordered>(a: T): Unit { }"))
        assertTrue(parseFails("fun broken<T: Ordered + Printable,>(a: T): Unit { }"))
    }

    @Test
    fun `product type rejects malformed field list`() {
        assertTrue(parseFails("type Person(name: String, name: String)"))
        assertTrue(parseFails("type Person(name: String,)"))
        assertTrue(parseFails("type Person(name String)"))
    }

    @Test
    fun `sum type rejects malformed variants and generic parameter lists`() {
        assertTrue(parseFails("type Broken ="))
        assertTrue(parseFails("type Broken = A |"))
        assertTrue(parseFails("type Broken = A || B"))
        assertTrue(parseFails("type Broken = A | A"))
        assertTrue(parseFails("type Broken<T,> = A"))
        assertTrue(parseFails("type Broken<> = A"))
    }

    @Test
    fun `sum type rejects malformed variant fields`() {
        assertTrue(parseFails("type Broken = A(value: Int,)"))
        assertTrue(parseFails("type Broken = A(value Int)"))
        assertTrue(parseFails("type Broken = A(value: Int, value: String)"))
    }

    @Test
    fun `sum type cannot be mutable`() {
        assertTrue(parseFails("type mut Maybe = Nothing"))
    }

    @Test
    fun `generic call rejects malformed type argument lists`() {
        assertTrue(parseFails("fun main(): Unit { val x: Maybe<Int> = Just<>(1) }"))
        assertTrue(parseFails("fun main(): Unit { val x: Maybe<Int> = Just<Int,>(1) }"))
        assertTrue(parseFails("fun main(): Unit { val x: Maybe<Int> = Just<Int>(1 }"))
    }

    @Test
    fun `field access rejects missing target and call suffix`() {
        assertTrue(parseFails("fun main(): Unit { val city: String = person. }"))
        assertTrue(parseFails("fun main(): Unit { val age: Int = person.age() }"))
    }

    @Test
    fun `type sign rejects empty generic argument list`() {
        assertTrue(parseFails("fun broken(a: Box<>): Unit { }"))
    }

    @Test
    fun `type sign rejects trailing comma in generic argument list`() {
        assertTrue(parseFails("fun broken(a: Pair<A,>): Unit { }"))
    }

    @Test
    fun `typeclass requires generic parameter list`() {
        assertTrue(parseFails("typeclass Printable { fun print(a: Int) }"))
    }

    @Test
    fun `typeclass generic parameter cannot have constraints`() {
        assertTrue(parseFails("typeclass Printable<A: Show> { fun print(a: A) }"))
    }

    @Test
    fun `typeclass requires exactly one generic parameter`() {
        assertTrue(parseFails("typeclass PairPrintable<A, B> { fun print(a: A, b: B) }"))
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
    fun `mutable static array declaration syntax is rejected`() {
        assertTrue(parseFails("fun main(): Unit { var xs: Int[3] = [1, 2, 3] }"))
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
    fun `literal starting statement is rejected`() {
        assertTrue(parseFails("fun main(): Unit { 1 }"))
    }

    @Test
    fun `malformed when syntax is rejected`() {
        assertTrue(parseFails("when (x) { 1 -> { } }"))
        assertTrue(parseFails("fun main(): Unit { when (x) { } }"))
        assertTrue(parseFails("fun main(): Unit { when (x) { 1 { } } }"))
        assertTrue(parseFails("fun main(): Unit { when (x) { 1 -> println(\"one\") } }"))
        assertTrue(parseFails("fun main(): Unit { when (x) { else -> { } 1 -> { } } }"))
    }

    @Test
    fun `bare return at end of file is rejected`() {
        assertTrue(parseFails("fun broken(): Unit { return"))
    }

    @Test
    fun `for desugaring keeps counter right border and increment shape`() {
        val root = parse("fun main(): Unit { for (i in from to end) { total = total + i } }")
        val forStatement = assertInstanceOf(ForStatementAstNode::class.java, root.functions.single().body.statements.first())
        val desugared = forStatement.desugaredContent.statements

        val counter = assertInstanceOf(ScalarVariableInitializationASTNode::class.java, desugared[0])
        assertEquals("i", counter.name)
        assertEquals(TypeRef("Int"), counter.type)
        assertTrue(counter.isMutable)

        val rightBorder = assertInstanceOf(ScalarVariableInitializationASTNode::class.java, desugared[1])
        assertEquals("_right_borderi", rightBorder.name)
        assertEquals(TypeRef("Int"), rightBorder.type)
        assertFalse(rightBorder.isMutable)

        val loop = assertInstanceOf(WhileStatementAstNode::class.java, desugared[2])
        val condition = assertInstanceOf(BinaryExpressionASTNode::class.java, loop.condition)
        assertEquals(BinaryOperator.Lt, condition.operator.kind)
        assertEquals("i", assertInstanceOf(VariableExpressionASTNode::class.java, condition.left).token.lexeme)
        assertEquals("_right_borderi", assertInstanceOf(VariableExpressionASTNode::class.java, condition.right).token.lexeme)

        val increment = assertInstanceOf(AssignmentStatementAstNode::class.java, loop.bodyBlock.statements.last())
        assertEquals("i", assertInstanceOf(VariableExpressionASTNode::class.java, increment.lValue).token.lexeme)
        val plus = assertInstanceOf(BinaryExpressionASTNode::class.java, increment.rValue)
        assertEquals(BinaryOperator.Plus, plus.operator.kind)
        assertEquals(1, assertInstanceOf(IntLiteralExpressionASTNode::class.java, plus.right).value)
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

        val context = ProgramGlobalContext()
        val clone = cloneSubtreeReplacingType(root, "A", "Int", context)
        val clonedFunction = clone.functions.single()
        val clonedLocal = assertInstanceOf(
            ScalarVariableInitializationASTNode::class.java,
            clonedFunction.body.statements.first(),
        )

        assertNotSame(root, clone)
        assertNotSame(originalFunction, clonedFunction)
        assertEquals(TypeRef("Box", listOf(TypeRef("Int"))), clonedFunction.result)
        assertEquals(listOf(FunctionArgument("a", context.intType)), clonedFunction.arguments)
        assertEquals(context.intType, clonedLocal.type)
        assertTrue(clonedFunction.genericParams.isEmpty())

        assertEquals(TypeRef("Box", listOf(TypeRef("A"))), originalFunction.result)
        assertEquals(listOf(FunctionArgument("a", TypeRef("A"))), originalFunction.arguments)
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
        val instantiated = instantiate(root)

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
        val instantiated = instantiate(root)
        val printValue = instantiated.functions.single { it.name == "printValue_String_Unit" }
        val printStatement = assertInstanceOf(CallFunctionStatementAstNode::class.java, printValue.body.statements.first())
        val printCall = assertInstanceOf(FunctionCallExpressionASTNode::class.java, printStatement.expression)

        assertNotNull(instantiated.functions.singleOrNull { it.name == "print_String_Unit" })
        assertEquals("print_String_Unit", printCall.name)
        assertTrue(instantiated.instances.isEmpty())
        assertTrue(instantiated.typeClasses.isEmpty())
    }

    @Test
    fun `template instantiator replaces generic local variable declaration types`() {
        val root = parse(
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
            """.trimIndent()
        )
        val instantiated = instantiate(root)

        val bubbleSort = instantiated.functions.single { it.name.startsWith("bubbleSort_") }
        val outerWhile = assertInstanceOf(WhileStatementAstNode::class.java, bubbleSort.body.statements[1])
        val innerWhile = assertInstanceOf(WhileStatementAstNode::class.java, outerWhile.bodyBlock.statements[1])
        val swapIf = assertInstanceOf(IfStatementAstNode::class.java, innerWhile.bodyBlock.statements.first())
        val tmp = assertInstanceOf(ScalarVariableInitializationASTNode::class.java, swapIf.thenBlock.statements.first())

        assertEquals("tmp", tmp.name)
        assertEquals(ProgramGlobalContext().intType, tmp.type)
    }

    @Test
    fun `template instantiator generates concrete sum type from generic usage`() {
        val root = parse(
            """
            type Maybe<T> = Just<T>(v: T) | Nothing

            fun id<T>(value: T): Maybe<T> {
                return Just<T>(value)
            }

            fun main() {
                val x: Maybe<Int> = id<Int>(200)
            }
            """.trimIndent()
        )

        val instantiated = instantiate(root)
        val maybeInt = assertInstanceOf(
            DeclareSumTypeASTNode::class.java,
            instantiated.types.single { it.name == "Maybe_Int" },
        )
        val just = maybeInt.variants.single { it.name == "Just" }
        val nothing = maybeInt.variants.single { it.name == "Nothing" }
        val id = instantiated.functions.single { it.name == "id_Int_Maybe_Int" }
        val main = instantiated.functions.single { it.name == "main" }
        val declaration = assertInstanceOf(
            ScalarVariableInitializationASTNode::class.java,
            main.body.statements.first(),
        )
        val call = assertInstanceOf(FunctionCallExpressionASTNode::class.java, declaration.expression)

        assertTrue(instantiated.types.none { it.name == "Maybe" })
        assertTrue(maybeInt.genericParams.isEmpty())
        assertTrue(just.typeArguments.isEmpty())
        assertEquals("v", just.fields.single().name)
        assertEquals("Int", just.fields.single().type.name)
        assertTrue(nothing.fields.isEmpty())
        assertEquals("Maybe_Int", id.result.name)
        assertEquals("Maybe_Int", declaration.type.name)
        assertTrue(declaration.type.arguments.isEmpty())
        assertEquals("id_Int_Maybe_Int", call.name)

        val returnStatement = assertInstanceOf(ReturnFunctionStatementAstNode::class.java, id.body.statements.first())
        val returnCall = assertInstanceOf(FunctionCallExpressionASTNode::class.java, returnStatement.expression)
        assertEquals("Just", returnCall.name)
        assertTrue(returnCall.typeArguments.isEmpty())
    }

    @Test
    fun `template instantiator rewrites explicit generic product constructor call`() {
        val root = parse(
            """
            type Box<T>(value: T)

            fun main() {
                val x: Box<Int> = Box<Int>(200)
            }
            """.trimIndent()
        )

        val instantiated = instantiate(root)
        val main = instantiated.functions.single { it.name == "main" }
        val declaration = assertInstanceOf(
            ScalarVariableInitializationASTNode::class.java,
            main.body.statements.first(),
        )
        val call = assertInstanceOf(FunctionCallExpressionASTNode::class.java, declaration.expression)

        assertInstanceOf(
            DeclareProductTypeASTNode::class.java,
            instantiated.types.single { it.name == "Box_Int" },
        )
        assertEquals("Box_Int", declaration.type.name)
        assertEquals("Box_Int", call.name)
        assertTrue(call.typeArguments.isEmpty())
    }

    @Test
    fun `template instantiator generates concrete product type from generic usage`() {
        val root = parse(
            """
            type Box<T>(value: T)

            fun makeBox<T>(value: T): Box<T> {
                return value
            }

            fun main() {
                val x: Box<Int> = makeBox<Int>(200)
            }
            """.trimIndent()
        )

        val instantiated = instantiate(root)
        val boxInt = assertInstanceOf(
            DeclareProductTypeASTNode::class.java,
            instantiated.types.single { it.name == "Box_Int" },
        )
        val makeBox = instantiated.functions.single { it.name == "makeBox_Int_Box_Int" }
        val main = instantiated.functions.single { it.name == "main" }
        val declaration = assertInstanceOf(
            ScalarVariableInitializationASTNode::class.java,
            main.body.statements.first(),
        )
        val call = assertInstanceOf(FunctionCallExpressionASTNode::class.java, declaration.expression)

        assertTrue(instantiated.types.none { it.name == "Box" })
        assertTrue(boxInt.genericParams.isEmpty())
        assertEquals("Int", boxInt.fields.getValue("value").name)
        assertEquals("Box_Int", makeBox.result.name)
        assertEquals("Box_Int", declaration.type.name)
        assertEquals("makeBox_Int_Box_Int", call.name)
    }

    @Test
    fun `template instantiator infers same generic from array and scalar arguments`() {
        val root = parse(
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
            """.trimIndent()
        )

        val context = ProgramGlobalContext()
        val instantiated = instantiate(root)
        val generated = instantiated.functions.single { it.name == "findIndex_Array_Int_Int_Int" }
        val main = instantiated.functions.single { it.name == "main" }
        val callDeclaration = assertInstanceOf(
            ScalarVariableInitializationASTNode::class.java,
            main.body.statements[2],
        )
        val call = assertInstanceOf(FunctionCallExpressionASTNode::class.java, callDeclaration.expression)

        assertTrue(generated.arguments[0].type.isArray)
        assertEquals(context.intType, generated.arguments[0].type.elementTypeOrNull)
        assertEquals(context.intType, generated.arguments[1].type)
        assertEquals(context.intType, generated.result)
        assertEquals("findIndex_Array_Int_Int_Int", call.name)
    }

    @Test
    fun `template instantiator supports multiple constraints on one generic`() {
        val root = parse(
            """
            typeclass Ordered<T> {
                fun less(a: T, b: T): Boolean
            }

            typeclass Printable<T> {
                fun toText(a: T): String
            }

            instance Ordered<Int> {
                fun less(a: Int, b: Int): Boolean {
                    return a < b
                }
            }

            instance Printable<Int> {
                fun toText(a: Int): String {
                    return intToString(a)
                }
            }

            fun describeMin<T: Ordered + Printable>(a: T, b: T): String {
                if (less(a, b)) {
                    return toText(a)
                }

                return toText(b)
            }

            fun main(): Unit {
                val text: String = describeMin(1, 2)
            }
            """.trimIndent()
        )

        val instantiated = instantiate(root)
        val generated = instantiated.functions.single { it.name == "describeMin_Int_Int_String" }
        val firstReturn = assertInstanceOf(
            ReturnFunctionStatementAstNode::class.java,
            assertInstanceOf(IfStatementAstNode::class.java, generated.body.statements.first()).thenBlock.statements.single(),
        )
        val firstCall = assertInstanceOf(FunctionCallExpressionASTNode::class.java, firstReturn.expression)
        val secondReturn = assertInstanceOf(ReturnFunctionStatementAstNode::class.java, generated.body.statements[1])
        val secondCall = assertInstanceOf(FunctionCallExpressionASTNode::class.java, secondReturn.expression)

        assertNotNull(instantiated.functions.singleOrNull { it.name == "less_Int_Int_Boolean" })
        assertNotNull(instantiated.functions.singleOrNull { it.name == "toText_Int_String" })
        assertEquals("toText_Int_String", firstCall.name)
        assertEquals("toText_Int_String", secondCall.name)
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

        assertTrue(instantiateEither(root).isLeft())
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

        assertTrue(instantiateEither(root).isLeft())
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

        assertTrue(instantiateEither(root).isLeft())
    }
}
