package org.nnezh.org.nnezh.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.nnezh.org.nnezh.ICGenerator.LLTACElement
import org.nnezh.org.nnezh.base.Type

class CTranspileTest {

    @Test
    fun `transpiles minimal main function`() {
        val tac = listOf(
            LLTACElement.function("main", Type.UnitType),
            LLTACElement.ret(),
        )

        assertEquals(
            listOf(
                "int main(int size, char** arg);",
                "int main(int size, char** arg) {",
                "init_runtime();",
                "return;",
                "}",
            ),
            CTranspile().compile(tac),
        )
    }

    @Test
    fun `emits prototypes before function bodies so forward calls compile`() {
        val tac = listOf(
            LLTACElement.function("foo", Type.UnitType),
            LLTACElement.call("bar", null, Type.UnitType, 0),
            LLTACElement.ret(),
            LLTACElement.function("bar", Type.UnitType),
            LLTACElement.ret(),
            LLTACElement.function("main", Type.UnitType),
            LLTACElement.call("foo", null, Type.UnitType, 0),
            LLTACElement.ret(),
        )

        assertEquals(
            listOf(
                "void foo();",
                "void bar();",
                "int main(int size, char** arg);",
                "void foo() {",
                "bar();",
                "return;",
                "}",
                "void bar() {",
                "return;",
                "}",
                "int main(int size, char** arg) {",
                "init_runtime();",
                "foo();",
                "return;",
                "}",
            ),
            CTranspile().compile(tac),
        )
    }

    @Test
    fun `function prototype includes parameters and return type`() {
        val tac = listOf(
            LLTACElement.function("sum", Type.IntType),
            LLTACElement.getParam("a", Type.IntType),
            LLTACElement.getParam("b", Type.IntType),
            LLTACElement.ret("a", Type.IntType),
        )
        val cLines = CTranspile().compile(tac)

        assertEquals("int sum(int a,int b);", cLines[0])
        assertEquals("int sum(int a,int b) {", cLines[1])
    }

    @Test
    fun `compile resets function body state between invocations`() {
        val transpiler = CTranspile()
        val tac = listOf(
            LLTACElement.function("main", Type.UnitType),
            LLTACElement.ret(),
        )

        val expected = listOf(
            "int main(int size, char** arg);",
            "int main(int size, char** arg) {",
            "init_runtime();",
            "return;",
            "}",
        )
        assertEquals(expected, transpiler.compile(tac))
        assertEquals(expected, transpiler.compile(tac))
    }
}
