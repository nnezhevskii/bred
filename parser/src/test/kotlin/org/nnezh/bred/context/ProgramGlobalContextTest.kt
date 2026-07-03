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
        assertTrue(context.doubleType.isPrimitive)
        assertTrue(context.stringType.isPrimitive)
        assertTrue(context.booleanType.isPrimitive)
        assertTrue(context.unitType.isPrimitive)
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
    fun `built in signatures are resolved from context registry`() {
        val context = ProgramGlobalContext()

        val println = context.builtInSignatures.single {
            it.name == "println" && it.args == listOf(context.stringType)
        }
        val readInt = context.builtInSignatures.single { it.name == "readInt" }

        assertEquals(context.unitType, println.resultType)
        assertEquals(context.intType, readInt.resultType)
    }

    private fun parse(source: String): ProgramRoot {
        val tokens = Lexer(source).tokenize().getOrElse { error("unexpected lexer error: $it") }
        return AbstractSyntaxTreeBuilder().build(tokens).getOrElse { error("unexpected parser error: $it") } as ProgramRoot
    }
}
