package org.nnezh.lltag

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.nnezh.bred.common.TypeSign
import org.nnezh.bred.context.ProgramGlobalContext
import org.nnezh.org.nnezh.ICGenerator.LLTACElement
import org.nnezh.org.nnezh.ICGenerator.LLTACFunc
import org.nnezh.org.nnezh.ICGenerator.LLTACGlobalStaticArray
import org.nnezh.org.nnezh.ICGenerator.LLTACInstruction
import org.nnezh.org.nnezh.ICGenerator.LLTACLabel
import org.nnezh.org.nnezh.ICGenerator.LLTACOperation
import org.nnezh.org.nnezh.ICGenerator.LLTACRetInstruction
import org.nnezh.org.nnezh.ICGenerator.Operand

internal fun verifyIR(types: ProgramGlobalContext, code: List<LLTACElement>, block: TACTestingFramework.() -> Unit) {
    TACTestingFramework(types, code).apply(block).expectEnd()
}

internal class TACTestingFramework(private val types: ProgramGlobalContext,
                                   private val elements: List<LLTACElement>) {
    private var cursor = 0

    /** Checks one element in order. Custom matchers can add assertions in [check]. */
    inline fun <reified T : LLTACElement> expectNext(crossinline check: (T) -> Unit = {}): T =
        matchNext(T::class.java) { it.also(check) }

    private fun <T : LLTACElement, R> matchNext(type: Class<T>, check: (T) -> R): R {
        val current = elements.getOrNull(cursor)
            ?: throw AssertionError("TAC[$cursor] expected ${type.simpleName}, reached end of input")
        val typed = assertInstanceOf(type, current, "TAC[$cursor] $current")
        val result = try {
            check(typed)
        } catch (failure: AssertionError) {
            throw AssertionError("TAC[$cursor] $current\n${failure.message}", failure)
        }
        cursor++
        return result
    }

    fun expectEnd() {
        elements.getOrNull(cursor)?.let { throw AssertionError("TAC[$cursor] expected end of input, found $it") }
    }

    fun expectGlobalArray(
        baseName: String,
        elementType: TypeSign,
        size: Int,
        initializers: List<Operand>? = null,
    ): Operand.Variable {
        val array = expectNext<LLTACGlobalStaticArray> {
            assertGeneratedName(it.name, "global_${baseName}_")
            assertEquals(elementType, it.type.elementTypeOrNull, "array element type")
            assertEquals(size, it.size, "array size")
            assertEquals(initializers, it.initialValues, "array initializers")
        }
        return Operand.Variable(array.name, array.type)
    }

    fun expectFunc(name: String, returnType: TypeSign) {
        expectNext<LLTACFunc> {
            assertEquals(name, it.name, "function name")
            assertEquals(returnType, it.type, "function return type")
        }
    }

    fun expectAssign(source: Operand, type: TypeSign): Operand.Variable =
        expectDestination(LLTACOperation.LLTAC_ASSIGN, "var", type, source)

    fun expectLoad(array: Operand.Variable, index: Operand.Variable): Operand.Variable {
        val elementType = requireNotNull(array.type.elementTypeOrNull) { "Expected array operand, got $array" }
        return expectDestination(LLTACOperation.LLTAC_LDX, "var", elementType, array, index)
    }

    fun expectNamedAssign(baseName: String, source: Operand.Variable): Operand.Variable =
        expectDestination(LLTACOperation.LLTAC_ASSIGN, "local_${baseName}_", source.type, source)

    fun expectRet(value: Operand.Variable? = null) {
        val expected = value?.let { it.name to it.type }
        expectNext<LLTACRetInstruction> { assertEquals(expected, it.value, "return value") }
    }

    fun expectParam(paramBaseName: String, paramType: TypeSign): Operand.Variable =
        matchNext(LLTACInstruction::class.java) {
            assertEquals(LLTACOperation.LLTAC_GET_PARAM, it.opcode, "opcode")
            assertNull(it.arg1, "first operand")
            assertNull(it.arg2, "second operand")
            val parameter = assertInstanceOf(Operand.Variable::class.java, it.destination, "parameter")
            assertGeneratedName(parameter.name, "param_${paramBaseName}_")
            assertEquals(paramType, parameter.type, "parameter type")
            parameter
        }

    fun expectJumpIfNot(condition: Operand.Variable): LLTACLabel =
        matchNext(LLTACInstruction::class.java) {
            assertEquals(LLTACOperation.LLTAC_JMP_IF_NOT, it.opcode, "opcode")
            assertEquals(types.booleanType, condition.type, "condition type")
            assertEquals(condition, it.arg1, "jump condition")
            assertNull(it.arg2, "second operand")
            val target = assertInstanceOf(Operand.Label::class.java, it.destination, "jump target")
            assertGeneratedName(target.label.name, "lbl")
            target.label
        }

    fun expectLabel(target: LLTACLabel) {
        expectNext<LLTACLabel> { assertEquals(target, it, "jump target") }
    }

    fun expectBinOp(
        opcode: LLTACOperation,
        destinationPrefix: String,
        destinationType: TypeSign,
        arg1: Operand,
        arg2: Operand
    ): Operand.Variable =
        expectDestination(opcode, destinationPrefix, destinationType, arg1, arg2)

    fun expectCallArgument(value: Operand.Variable) {
        expectNext<LLTACInstruction> {
            assertEquals(LLTACOperation.LLTAC_PARAM, it.opcode, "opcode")
            assertEquals(value, it.destination, "call argument")
            assertNull(it.arg1, "first operand")
            assertNull(it.arg2, "second operand")
        }
    }

    fun expectCall(
        name: String,
        returnType: TypeSign,
        argumentCount: Int,
        localName: String? = null,
    ): Operand.Variable =
        expectDestination(
            LLTACOperation.LLTAC_CALL,
            localName?.let { "local_${it}_" } ?: "var",
            returnType,
            Operand.FunctionCall(name, returnType),
            Operand.IntConst(argumentCount.toLong()),
        )

    fun expectUnitCall(name: String, argumentCount: Int) {
        expectNext<LLTACInstruction> {
            assertEquals(LLTACOperation.LLTAC_CALL, it.opcode, "opcode")
            assertNull(it.destination, "call destination")
            assertEquals(Operand.FunctionCall(name, types.unitType), it.arg1, "call target")
            assertEquals(Operand.IntConst(argumentCount.toLong()), it.arg2, "argument count")
        }
    }

    private fun expectDestination(
        opcode: LLTACOperation,
        namePrefix: String,
        type: TypeSign,
        arg1: Operand,
        arg2: Operand? = null,
    ): Operand.Variable =
        matchNext(LLTACInstruction::class.java) {
            assertEquals(opcode, it.opcode, "opcode")
            assertEquals(arg1, it.arg1, "first operand")
            assertEquals(arg2, it.arg2, "second operand")
            val destination = assertInstanceOf(Operand.Variable::class.java, it.destination, "destination")
            assertGeneratedName(destination.name, namePrefix)
            assertEquals(type, destination.type, "destination type")
            destination
        }

    private fun assertGeneratedName(name: String, prefix: String) {
        if (!name.matches(Regex("${Regex.escape(prefix)}[0-9]+"))) {
            throw AssertionError("Expected generated name ${prefix}<id>, got $name")
        }
    }
}
