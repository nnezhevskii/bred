package org.nnezh.bred.context

import arrow.core.getOrElse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.bred.ast.AbstractSyntaxTreeBuilder
import org.nnezh.bred.ast.GenericParam
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.common.TypeNames
import org.nnezh.bred.common.TypeRef
import org.nnezh.lexer.Lexer

class ProgramGlobalContextTest {
    @Test
    fun `context initializes primitive and unit types`() {
        val context = ProgramGlobalContext()

        assertTrue(context.intType.isPrimitive)
        assertTrue(context.longType.isPrimitive)
        assertTrue(context.doubleType.isPrimitive)
        assertTrue(context.stringType.isPrimitive)
        assertTrue(context.booleanType.isPrimitive)
        assertTrue(context.unitType.isPrimitive)
        assertSame(context.longType, context.findType(TypeNames.LONG))
        assertSame(context.unitType, context.findType(TypeNames.UNIT))
    }

    @Test
    fun `array factory creates resolved array type with element accessor`() {
        val context = ProgramGlobalContext()

        val array = context.arrayOf(context.intType)

        assertTrue(array.isArray)
        assertEquals(listOf(context.intType), array.arguments)
        assertEquals(context.intType, array.elementTypeOrNull)
        assertTrue(array in context.allTypes())
    }

    @Test
    fun `unknown concrete type is rejected during resolution`() {
        val root = parse("fun broken(value: Missing): Unit { }")
        val context = ProgramContextCollector().collect(root)

        assertTrue(ProgramTypeResolver(context).resolve(root).isLeft())
        assertNull(context.resolve(TypeRef("Missing")))
    }

    @Test
    fun `unknown product field type is rejected during resolution`() {
        val root = parse("type Person(address: Missing)")
        val context = ProgramContextCollector().collect(root)

        assertTrue(ProgramTypeResolver(context).resolve(root).isLeft())
        assertNull(context.productFields(context.findType("Person")!!))
    }

    @Test
    fun `unknown sum payload type is rejected during resolution`() {
        val root = parse("type Maybe = Nothing | Just<Missing>")
        val context = ProgramContextCollector().collect(root)

        assertTrue(ProgramTypeResolver(context).resolve(root).isLeft())
        assertNull(context.sumDefinition(context.findType("Maybe")!!))
    }

    @Test
    fun `resolved context records forward product fields mutability and constructor`() {
        val context = resolvedContext(
            """
            type Person(address: Address)
            type mut Address(city: String)
            """.trimIndent()
        )
        val person = context.findType("Person")!!
        val address = context.findType("Address")!!

        assertEquals(address, context.productFields(person)?.get("address"))
        assertFalse(context.isMutableProduct(person))
        assertTrue(context.isMutableProduct(address))

        val constructor = context.constructorsByName("Person").single()
        assertEquals(ConstructorKind.PRODUCT, constructor.kind)
        assertEquals(listOf(address), constructor.arguments)
        assertEquals(person, constructor.resultType)
    }

    @Test
    fun `resolved context records sum variant constructors and payload metadata`() {
        val context = resolvedContext("type MaybeInt = Nothing | Just<Int> | Pair<Int, String>")
        val maybeInt = context.findType("MaybeInt")!!
        val definition = context.sumDefinition(maybeInt)!!

        assertEquals(listOf("Nothing", "Just", "Pair"), definition.variants.map { it.name })
        assertEquals(listOf(0, 1, 2), definition.variants.map { it.tag })
        assertTrue(definition.variants[0].fields.isEmpty())
        assertEquals("value0", definition.variants[1].fields.single().name)
        assertEquals(context.intType, definition.variants[1].fields.single().type)
        assertEquals(listOf("value0", "value1"), definition.variants[2].fields.map { it.name })
        assertEquals(listOf(context.intType, context.stringType), definition.variants[2].fields.map { it.type })

        val nothing = context.constructorsByName("Nothing").single()
        val just = context.constructorsByName("Just").single()
        val pair = context.constructorsByName("Pair").single()

        assertEquals(ConstructorKind.SUM_VARIANT, nothing.kind)
        assertEquals(emptyList<TypeRef>(), nothing.arguments)
        assertEquals(maybeInt, nothing.resultType)
        assertEquals(listOf(context.intType), just.arguments)
        assertEquals(maybeInt, just.resultType)
        assertEquals(listOf(context.intType, context.stringType), pair.arguments)
        assertEquals(maybeInt, pair.resultType)
    }

    @Test
    fun `generic type resolves only inside generic scope`() {
        val context = ProgramGlobalContext()
        val scope = context.genericScope(listOf(GenericParam("T", emptyList())))

        val generic = context.resolve(TypeRef("T"), scope)

        assertNotNull(generic)
        assertFalse(generic!!.isPrimitive)
        assertEquals("T", generic.name)
        assertNull(context.resolve(TypeRef("T")))
    }

    @Test
    fun `resolved named user type preserves generic arguments`() {
        val context = ProgramGlobalContext()
        context.addType("Box", isPrimitive = false)

        val boxOfInt = context.resolve(TypeRef("Box", listOf(TypeRef("Int"))))

        assertNotNull(boxOfInt)
        assertEquals("Box", boxOfInt!!.name)
        assertEquals(listOf(context.intType), boxOfInt.arguments)
        assertEquals("Box<Int>", boxOfInt.render())
        assertEquals("Box_Int", boxOfInt.toManglePart())
    }

    @Test
    fun `extern function signatures are resolved from context registry`() {
        val context = ProgramGlobalContext()

        val println = context.externFunctionSignatures.single {
            it.name == "println" && it.args == listOf(context.stringType)
        }
        val readInt = context.externFunctionSignatures.single { it.name == "readInt" }
        val currentTimeMillis = context.externFunctionSignatures.single { it.name == "currentTimeMillis" }

        assertEquals(context.unitType, println.resultType)
        assertEquals(context.intType, readInt.resultType)
        assertTrue(currentTimeMillis.args.isEmpty())
        assertEquals(context.longType, currentTimeMillis.resultType)
        assertEquals(context.externFunctionSignatures, context.builtInSignatures)
    }

    private fun parse(source: String): ProgramRoot {
        val tokens = Lexer(source).tokenize().getOrElse { error("unexpected lexer error: $it") }
        return AbstractSyntaxTreeBuilder().build(tokens).getOrElse { error("unexpected parser error: $it") } as ProgramRoot
    }

    private fun resolvedContext(source: String): ProgramGlobalContext {
        val root = parse(source)
        val initialContext = ProgramContextCollector().collect(root)
        val resolved = ProgramTypeResolver(initialContext)
            .resolve(root)
            .getOrElse { error("unexpected type resolution error: $it") }
        return ProgramContextCollector().collect(resolved)
    }
}
