package org.nnezh.lltag

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
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

    fun expectParam(paramBaseName: String, paramType: TypeSign): Operand.Variable {
        var destination: Operand.Variable? = null

        expectNext<LLTACInstruction> {
            assert(it.opcode == LLTACOperation.LLTAC_GET_PARAM)
            assert(it.destination is Operand.Variable)
            destination = it.destination as Operand.Variable

            assertGeneratedName(destination.name, "param_${paramBaseName}_")
            assert(destination.type == paramType)

        }
        return destination!!
    }

    fun expectJumpIfNot(
        condition: Operand,
    ): LLTACLabel {

        assert(condition.type == types.booleanType )

        var result: LLTACLabel? = null
        expectNext<LLTACInstruction> {
            assert(it.opcode == LLTACOperation.LLTAC_JMP_IF_NOT)
            assert(it.arg1 == condition)
            assert(it.destination is Operand.Label)
            assert(it.arg2 == null)

            val target = it.destination as Operand.Label
            assertGeneratedName(target.label.name, "lbl")
            result = target.label
        }

        return result!!
    }

    fun expectBinOp(
        opcode: LLTACOperation,
        destinationPrefix: String,
        destinationType: TypeSign,
        arg1: Operand,
        arg2: Operand
    ): Operand {
        val destination: Operand.Variable = expectDestination(
            opcode = opcode,
            namePrefix = destinationPrefix,
            type = destinationType,
            arg1 = arg1,
            arg2 = arg2
        )
//        expectNext<LLTACInstruction> {
//            e
//            assert(it.opcode == opcode)
//            assert(it.destination is Operand.Variable)
//            destination = it.destination as Operand.Variable
//            assert(destination.name.startsWith(destinationPrefix))
//            assert(destination.type == destinationType)
//            assert(it.arg1 == arg1 )
//            assert(it.arg2 == arg2)
//
//        }

        return destination
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
