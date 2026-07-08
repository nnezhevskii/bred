package org.nnezh.org.nnezh.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.org.nnezh.ICGenerator.LLTACElement
import org.nnezh.org.nnezh.ICGenerator.LLTACInstruction
import org.nnezh.org.nnezh.ICGenerator.LLTACOperation
import org.nnezh.org.nnezh.ICGenerator.Operand
import org.nnezh.bred.common.TypeNames
import org.nnezh.bred.common.TypeRegistry
import org.nnezh.bred.common.TypeSign

class CTranspileTest {
    private val registry = TypeRegistry()
    private val int = registry.declarePrimitive(TypeNames.INT)
    private val long = registry.declarePrimitive(TypeNames.LONG)
    private val string = registry.declarePrimitive(TypeNames.STRING)
    private val double = registry.declarePrimitive(TypeNames.DOUBLE)
    private val boolean = registry.declarePrimitive(TypeNames.BOOLEAN)
    private val unit = registry.declarePrimitive(TypeNames.UNIT)
    private val intArray: TypeSign
        get() = registry.arrayOf(int)
    private val longArray: TypeSign
        get() = registry.arrayOf(long)

    @Test
    fun `transpiles minimal main function`() {
        val tac = listOf(
            LLTACElement.function("main", unit),
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
            LLTACElement.function("foo", unit),
            LLTACElement.call("bar", null, unit, 0),
            LLTACElement.ret(),
            LLTACElement.function("bar", unit),
            LLTACElement.ret(),
            LLTACElement.function("main", unit),
            LLTACElement.call("foo", null, unit, 0),
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
            LLTACElement.function("sum", int),
            LLTACElement.getParam("a", int),
            LLTACElement.getParam("b", int),
            LLTACElement.ret("a", int),
        )
        val cLines = CTranspile().compile(tac)

        assertEquals("int sum(int a,int b);", cLines[0])
        assertEquals("int sum(int a,int b) {", cLines[1])
    }

    @Test
    fun `function prototype renders Long parameters and return as long long`() {
        val tac = listOf(
            LLTACElement.function("next", long),
            LLTACElement.getParam("value", long),
            LLTACElement.ret("value", long),
        )
        val cLines = CTranspile().compile(tac)

        assertEquals("long long next(long long value);", cLines[0])
        assertEquals("long long next(long long value) {", cLines[1])
    }

    @Test
    fun `function prototype renders array parameter as pointer`() {
        val tac = listOf(
            LLTACElement.function("sort", unit),
            LLTACElement.getParam("arr", intArray),
            LLTACElement.getParam("size", int),
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
            LLTACElement.function("main", unit),
            LLTACElement.alloc("arr", intArray, 3),
            LLTACElement.assign("i", int, 0),
            LLTACElement.assign("value", int, 42),
            LLTACElement.store("arr", "i", "value", intArray, int, int),
            LLTACElement.load("arr", "read", "i", intArray, int, int),
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
                arrayType = intArray,
                size = 3,
                initialValues = listOf(Operand.IntConst(1), Operand.IntConst(2), Operand.IntConst(3)),
            ),
            LLTACElement.function("main", unit),
            LLTACElement.assign("i", int, 1),
            LLTACElement.load("values", "read", "i", intArray, int, int),
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
    fun `long locals globals constants and operations transpile to long long`() {
        val tac = listOf(
            LLTACElement.globalArray(
                name = "values",
                arrayType = longArray,
                size = 2,
                initialValues = listOf(Operand.LongConst(4), Operand.LongConst(5)),
            ),
            LLTACElement.function("main", unit),
            LLTACElement.assign("i", int, 1),
            LLTACElement.assign("value", long, 7),
            LLTACElement.load("values", "read", "i", longArray, long, int),
            LLTACElement.binOp(
                opcode = LLTACOperation.LLTAC_ADD,
                varName = "sum",
                varType = long,
                arg1Name = "value",
                arg1Type = long,
                arg2Name = "read",
                arg2Type = long,
            ),
            LLTACElement.binOp(
                opcode = LLTACOperation.LLTAC_SUB,
                varName = "diff",
                varType = long,
                arg1Name = "sum",
                arg1Type = long,
                arg2Name = "value",
                arg2Type = long,
            ),
            LLTACElement.binOp(
                opcode = LLTACOperation.LLTAC_DIV,
                varName = "quotient",
                varType = long,
                arg1Name = "diff",
                arg1Type = long,
                arg2Name = "value",
                arg2Type = long,
            ),
            LLTACElement.binOp(
                opcode = LLTACOperation.LLTAC_MOD,
                varName = "remainder",
                varType = long,
                arg1Name = "diff",
                arg1Type = long,
                arg2Name = "value",
                arg2Type = long,
            ),
            LLTACElement.ret(),
        )

        assertEquals(
            listOf(
                "long long values[2] = {4LL, 5LL};",
                "int main(int size, char** arg);",
                "int main(int size, char** arg) {",
                "init_runtime();",
                "int i;",
                "long long value, read, sum, diff, quotient, remainder;",
                "i = 1;",
                "value = 7LL;",
                "read=values[i];",
                "sum=value+read;",
                "diff=sum-value;",
                "quotient=diff/value;",
                "remainder=diff%value;",
                "return;",
                "}",
            ),
            CTranspile().compile(tac),
        )
    }

    @Test
    fun `currentTimeMillis call result transpiles as long long`() {
        val tac = listOf(
            LLTACElement.function("main", unit),
            LLTACElement.call("currentTimeMillis", "now", long, 0),
            LLTACElement.ret(),
        )

        assertEquals(
            listOf(
                "int main(int size, char** arg);",
                "int main(int size, char** arg) {",
                "init_runtime();",
                "long long now;",
                "now = currentTimeMillis();",
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
                arrayType = intArray,
                size = 2,
                initialValues = null,
            ),
            LLTACElement.function("main", unit),
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
            LLTACElement.function("main", unit),
            LLTACElement.assign("prefix", string, "checksum = "),
            LLTACElement.assign("value", string, "215"),
            LLTACElement.binOp(
                opcode = LLTACOperation.LLTAC_ADD,
                varName = "line",
                varType = string,
                arg1Name = "prefix",
                arg1Type = string,
                arg2Name = "value",
                arg2Type = string,
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
            LLTACElement.function("main", unit),
            LLTACElement.assign("msg", string, "line1\n\"quoted\"\\"),
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
            LLTACElement.function("main", unit),
            LLTACElement.assign("flag", boolean, true),
            LLTACElement.unOp(
                opcode = LLTACOperation.LLTAC_NOT,
                varName = "negated",
                varType = boolean,
                arg1Name = "flag",
                arg1Type = boolean,
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
    fun `sum constructor calls render as designated struct literals`() {
        val maybeInt = registry.declareUserType("MaybeInt")
        val tac = listOf(
            LLTACElement.structDef(maybeInt),
            LLTACElement.field("tag", int),
            LLTACElement.field("Just_value0", int),
            LLTACElement.endStructDef(),
            LLTACElement.sumVariant(maybeInt, "Nothing", 0),
            LLTACElement.sumVariant(maybeInt, "Just", 1),
            LLTACElement.sumVariantField(maybeInt, "Just", "Just_value0", int),
            LLTACElement.function("main", unit),
            LLTACElement.assign("value", int, 7),
            LLTACElement.param("value", int),
            LLTACElement.call("Just", "present", maybeInt, 1),
            LLTACElement.call("Nothing", "absent", maybeInt, 0),
            LLTACElement.ret(),
        )

        assertEquals(
            listOf(
                "typedef struct MaybeInt { int tag; int Just_value0; } MaybeInt;",
                "int main(int size, char** arg);",
                "int main(int size, char** arg) {",
                "init_runtime();",
                "int value;",
                "MaybeInt present, absent;",
                "value = 7;",
                "present = (MaybeInt){.tag = 1, .Just_value0 = value};",
                "absent = (MaybeInt){.tag = 0};",
                "return;",
                "}",
            ),
            CTranspile().compile(tac),
        )
    }

    @Test
    fun `sum constructor literals preserve named payload field order`() {
        val response = registry.declareUserType("Response")
        val tac = listOf(
            LLTACElement.structDef(response),
            LLTACElement.field("tag", int),
            LLTACElement.field("Success_value", int),
            LLTACElement.field("Success_message", string),
            LLTACElement.endStructDef(),
            LLTACElement.sumVariant(response, "Success", 0),
            LLTACElement.sumVariantField(response, "Success", "Success_value", int),
            LLTACElement.sumVariantField(response, "Success", "Success_message", string),
            LLTACElement.function("main", unit),
            LLTACElement.assign("code", int, 200),
            LLTACElement.assign("message", string, "ok"),
            LLTACElement.param("code", int),
            LLTACElement.param("message", string),
            LLTACElement.call("Success", "response", response, 2),
            LLTACElement.ret(),
        )

        assertEquals(
            listOf(
                "typedef struct Response { int tag; int Success_value; String Success_message; } Response;",
                "int main(int size, char** arg);",
                "int main(int size, char** arg) {",
                "init_runtime();",
                "int code;",
                "String message;",
                "Response response;",
                "code = 200;",
                "message = create_string(\"ok\", strlen(\"ok\"));",
                "response = (Response){.tag = 0, .Success_value = code, .Success_message = message};",
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
            LLTACElement.function("main", unit),
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
            LLTACElement.function("main", unit),
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
