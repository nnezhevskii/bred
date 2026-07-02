package org.nnezh.org.nnezh.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.org.nnezh.ICGenerator.LLTACElement
import org.nnezh.org.nnezh.ICGenerator.LLTACInstruction
import org.nnezh.org.nnezh.ICGenerator.LLTACOperation
import org.nnezh.org.nnezh.ICGenerator.Operand
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
    fun `function prototype renders array parameter as pointer`() {
        val tac = listOf(
            LLTACElement.function("sort", Type.UnitType),
            LLTACElement.getParam("arr", Type.StaticArrayType(Type.IntType, 0)),
            LLTACElement.getParam("size", Type.IntType),
            LLTACElement.ret(),
        )

        assertEquals(
            listOf(
                "void sort(int* arr,int size);",
                "void sort(int* arr,int size) {",
                "return;",
                "}",
            ),
            CTranspile().compile(tac),
        )
    }

    @Test
    fun `emits local static array declaration store and load`() {
        val tac = listOf(
            LLTACElement.function("main", Type.UnitType),
            LLTACElement.alloc("arr", Type.StaticArrayType(Type.IntType, 3), 3),
            LLTACElement.assign("i", Type.IntType, 0),
            LLTACElement.assign("value", Type.IntType, 42),
            LLTACElement.store("arr", "i", "value", Type.IntType),
            LLTACElement.load("arr", "read", "i", Type.IntType),
            LLTACElement.ret(),
        )

        assertEquals(
            listOf(
                "int main(int size, char** arg);",
                "int main(int size, char** arg) {",
                "init_runtime();",
                "int arr[3];",
                "int i, value, read;",
                "i = 0;",
                "value = 42;",
                "arr[i]=value;",
                "read=arr[i];",
                "return;",
                "}",
            ),
            CTranspile().compile(tac),
        )
    }

    @Test
    fun `emits global static array before function prototypes`() {
        val tac = listOf(
            LLTACElement.globalArray(
                name = "values",
                type = Type.StaticArrayType(Type.IntType, 3),
                initialValues = listOf(Operand.IntConst(1), Operand.IntConst(2), Operand.IntConst(3)),
            ),
            LLTACElement.function("main", Type.UnitType),
            LLTACElement.assign("i", Type.IntType, 1),
            LLTACElement.load("values", "read", "i", Type.IntType),
            LLTACElement.ret(),
        )

        assertEquals(
            listOf(
                "int values[3] = {1, 2, 3};",
                "int main(int size, char** arg);",
                "int main(int size, char** arg) {",
                "init_runtime();",
                "int i, read;",
                "i = 1;",
                "read=values[i];",
                "return;",
                "}",
            ),
            CTranspile().compile(tac),
        )
    }

    @Test
    fun `emits global static array without initializer`() {
        val tac = listOf(
            LLTACElement.globalArray(
                name = "values",
                type = Type.StaticArrayType(Type.IntType, 2),
                initialValues = null,
            ),
            LLTACElement.function("main", Type.UnitType),
            LLTACElement.ret(),
        )

        assertEquals(
            listOf(
                "int values[2];",
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
    fun `string addition emits runtime concat call`() {
        val tac = listOf(
            LLTACElement.function("main", Type.UnitType),
            LLTACElement.assign("prefix", Type.StringType, "checksum = "),
            LLTACElement.assign("value", Type.StringType, "215"),
            LLTACElement.binOp(
                opcode = LLTACOperation.LLTAC_ADD,
                varName = "line",
                varType = Type.StringType,
                arg1Name = "prefix",
                arg1Type = Type.StringType,
                arg2Name = "value",
                arg2Type = Type.StringType,
            ),
            LLTACElement.ret(),
        )

        assertEquals(
            listOf(
                "int main(int size, char** arg);",
                "int main(int size, char** arg) {",
                "init_runtime();",
                "String prefix, value, line;",
                "prefix = create_string(\"checksum = \", strlen(\"checksum = \"));",
                "value = create_string(\"215\", strlen(\"215\"));",
                "line=concat(prefix,value);",
                "return;",
                "}",
            ),
            CTranspile().compile(tac),
        )
    }

    @Test
    fun `string assignment escapes c string literal contents`() {
        val tac = listOf(
            LLTACElement.function("main", Type.UnitType),
            LLTACElement.assign("msg", Type.StringType, "line1\n\"quoted\"\\"),
            LLTACElement.ret(),
        )

        assertEquals(
            listOf(
                "int main(int size, char** arg);",
                "int main(int size, char** arg) {",
                "init_runtime();",
                "String msg;",
                """msg = create_string("line1\n\"quoted\"\\", strlen("line1\n\"quoted\"\\"));""",
                "return;",
                "}",
            ),
            CTranspile().compile(tac),
        )
    }

    @Test
    fun `not operation assigns logical negation`() {
        val tac = listOf(
            LLTACElement.function("main", Type.UnitType),
            LLTACElement.assign("flag", Type.BoolType, true),
            LLTACElement.unOp(
                opcode = LLTACOperation.LLTAC_NOT,
                varName = "negated",
                varType = Type.BoolType,
                arg1Name = "flag",
                arg1Type = Type.BoolType,
            ),
            LLTACElement.ret(),
        )

        assertEquals(
            listOf(
                "int main(int size, char** arg);",
                "int main(int size, char** arg) {",
                "init_runtime();",
                "bool flag, negated;",
                "flag = true;",
                "negated=!flag;",
                "return;",
                "}",
            ),
            CTranspile().compile(tac),
        )
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

    @Test
    fun `malformed tac reports instruction index opcode and expected operand`() {
        val tac = listOf(
            LLTACElement.function("main", Type.UnitType),
            LLTACInstruction(
                opcode = LLTACOperation.LLTAC_ASSIGN,
                destination = null,
                arg1 = Operand.IntConst(1),
            ),
        )

        val error = assertThrows(CTranspileException::class.java) {
            CTranspile().compile(tac)
        }

        val message = error.message ?: ""
        assertTrue(message.contains("instruction #1"), message)
        assertTrue(message.contains("ASSIGN"), message)
        assertTrue(message.contains("expected destination"), message)
    }
}
