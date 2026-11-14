package org.nnezh.lltag

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.nnezh.bred.common.TypeSign
import org.nnezh.bred.context.ProgramGlobalContext
import org.nnezh.org.nnezh.ICGenerator.LLTACElement
import org.nnezh.org.nnezh.ICGenerator.LLTACFunc
import org.nnezh.org.nnezh.ICGenerator.LLTACGlobalStaticArray
import org.nnezh.org.nnezh.ICGenerator.LLTACInstruction
import org.nnezh.org.nnezh.ICGenerator.LLTACLabel
import org.nnezh.org.nnezh.ICGenerator.LLTACOperation
import org.nnezh.org.nnezh.ICGenerator.LLTACOperation.*
import org.nnezh.org.nnezh.ICGenerator.LLTACRetInstruction
import org.nnezh.org.nnezh.ICGenerator.Operand
import org.nnezh.org.nnezh.ICGenerator.Operand.Variable
import org.nnezh.org.nnezh.ICGenerator.Operand.TypeConst

internal fun verifyIR(types: ProgramGlobalContext, code: List<LLTACElement>, block: TACTestingFramework.() -> Unit) {
    TACTestingFramework(types, code).apply(block).expectEnd()
}

internal class TACTestingFramework(private val types: ProgramGlobalContext,
                                   private val elements: List<LLTACElement>) {
    private var cursor = 0
    private val definedLabels = mutableSetOf<LLTACLabel>()
    private val pendingJumps = mutableMapOf<LLTACLabel, Int>()
    private val temporaryNames = mutableSetOf<String>()
    private val temporaryNamePattern = Regex("var[0-9]+")

    /** Checks one element in order. Custom matchers can add assertions in [check]. */
    inline fun <reified T : LLTACElement> expectNext(crossinline check: (T) -> Unit = {}): T =
        matchNext(T::class.java) { it.also(check) }

    private fun <T : LLTACElement, R> matchNext(type: Class<T>, check: (T) -> R): R {
        val current = elements.getOrNull(cursor)
            ?: throw AssertionError("TAC[$cursor] expected ${type.simpleName}, reached end of input")
        val typed = assertInstanceOf(type, current, "TAC[$cursor] $current")
        val result = try {
            val value = check(typed)
            recordElement(current)
            value
        } catch (failure: AssertionError) {
            throw AssertionError("TAC[$cursor] $current\n${failure.message}", failure)
        }
        cursor++
        return result
    }

    fun expectEnd() {
        elements.getOrNull(cursor)?.let { throw AssertionError("TAC[$cursor] expected end of input, found $it") }
        assertJumpsResolved()
    }

    private fun recordElement(element: LLTACElement) {
        when {
            element is LLTACFunc -> {
                assertJumpsResolved()
                definedLabels.clear()
                temporaryNames.clear()
            }
            element is LLTACLabel -> {
                assertTrue(definedLabels.add(element), "Duplicate label ${element.name}")
                pendingJumps.remove(element)
            }
            element is LLTACInstruction &&
                (element.opcode == LLTACOperation.LLTAC_JMP || element.opcode == LLTACOperation.LLTAC_JMP_IF_NOT) -> {
                val target = assertInstanceOf(Operand.Label::class.java, element.destination, "jump target").label
                if (target !in definedLabels) pendingJumps.putIfAbsent(target, cursor)
            }
            element is LLTACInstruction && element.opcode.writesDestination -> {
                val destination = element.destination as? Operand.Variable ?: return
                if (temporaryNamePattern.matches(destination.name)) temporaryNames.add(destination.name)
            }
        }
    }

    private val LLTACOperation.writesDestination: Boolean
        get() = when (this) {
            LLTAC_GET_PARAM, LLTAC_ALLOC, LLTAC_ASSIGN, LLTAC_CALL, LLTAC_LDX, LLTAC_LDF,
            LLTAC_ADD, LLTAC_ADD_I32, LLTAC_SUB, LLTAC_MUL, LLTAC_DIV, LLTAC_MOD, LLTAC_NEG,
            LLTAC_EQ, LLTAC_NEQ, LLTAC_LT, LLTAC_GT, LLTAC_LE, LLTAC_GE, LLTAC_AND, LLTAC_OR, LLTAC_NOT -> true
            LLTAC_PARAM, LLTAC_RET, LLTAC_JMP, LLTAC_JMP_IF_NOT, LLTAC_STX, LLTAC_STF,
            LLTAC_STRUCT_DEF, LLTAC_STRUCT_DEF_END, LLTAC_FIELD, LLTAC_SUM_VARIANT, LLTAC_SUM_VARIANT_FIELD -> false
        }

    private fun assertJumpsResolved() {
        val (label, position) = pendingJumps.entries.firstOrNull() ?: return
        throw AssertionError("TAC[$position] jump to undefined label ${label.name} in this function")
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

    fun expectAssign(source: Operand, type: TypeSign, localName: String? = null): Operand.Variable =
        expectDestination(LLTACOperation.LLTAC_ASSIGN, localName?.let { "local_${it}_" } ?: "var", type, source)

    fun intLiteral(n: Int, local: String? = null): Operand.Variable =
        expectAssign(Operand.IntConst(n.toLong()), types.intType, local)

    fun text(value: String, local: String? = null): Operand.Variable =
        expectAssign(Operand.StringConst(value), types.stringType, local)

    fun boolLiteral(value: Boolean, local: String? = null): Variable =
        expectAssign(Operand.BoolConst(value), types.booleanType, local)

    fun doubleLiteral(value: Double, local: String? = null): Variable =
        expectAssign(Operand.DoubleConst(value), types.doubleType, local)

    fun expectAlloc(baseName: String, elementType: TypeSign, size: Int): Operand.Variable =
        expectDestination(
            LLTACOperation.LLTAC_ALLOC, "local_${baseName}_", types.arrayOf(elementType),
            Operand.TypeConst(elementType), Operand.IntConst(size.toLong()),
        )

    fun expectLoad(array: Operand.Variable, index: Operand.Variable): Operand.Variable {
        val elementType = requireNotNull(array.type.elementTypeOrNull) { "Expected array operand, got $array" }
        return expectDestination(LLTACOperation.LLTAC_LDX, "var", elementType, array, index)
    }

    fun expectNamedAssign(baseName: String, source: Operand.Variable): Operand.Variable =
        expectDestination(LLTACOperation.LLTAC_ASSIGN, "local_${baseName}_", source.type, source)

    /** Checks an opcode and all three operands, including empty slots. */
    fun expectInstruction(opcode: LLTACOperation, destination: Operand?, arg1: Operand? = null, arg2: Operand? = null) {
        expectNext<LLTACInstruction> {
            assertEquals(opcode, it.opcode, "opcode")
            assertEquals(destination, it.destination, "destination")
            assertEquals(arg1, it.arg1, "first operand")
            assertEquals(arg2, it.arg2, "second operand")
        }
    }

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

    fun expectLabel(target: LLTACLabel? = null): LLTACLabel =
        expectNext<LLTACLabel> {
            assertGeneratedName(it.name, "lbl")
            if (target != null) assertEquals(target, it, "jump target")
        }

    fun expectJump(target: LLTACLabel? = null): LLTACLabel =
        matchNext(LLTACInstruction::class.java) {
            assertEquals(LLTACOperation.LLTAC_JMP, it.opcode, "opcode")
            assertNull(it.arg1, "first operand")
            assertNull(it.arg2, "second operand")
            val label = assertInstanceOf(Operand.Label::class.java, it.destination, "jump target").label
            assertGeneratedName(label.name, "lbl")
            if (target != null) assertEquals(target, label, "jump target")
            label
        }

    fun expectNeg(source: Operand.Variable): Operand.Variable =
        expectDestination(LLTACOperation.LLTAC_NEG, "var", source.type, source)

    fun expectBinOp(
        opcode: LLTACOperation,
        destinationPrefix: String,
        destinationType: TypeSign,
        arg1: Operand,
        arg2: Operand
    ): Operand.Variable =
        expectDestination(opcode, destinationPrefix, destinationType, arg1, arg2)

    fun compare(op: LLTACOperation, left: Variable, right: Variable, local: String? = null): Variable =
        expectBinOp(op, local?.let { "local_${it}_" } ?: "var", types.booleanType, left, right)

    fun arithmetic(op: LLTACOperation, left: Variable, right: Variable, local: String? = null): Variable =
        expectBinOp(op, local?.let { "local_${it}_" } ?: "var", left.type, left, right)

    fun expectField(owner: Variable, name: String, type: TypeSign, local: String? = null): Variable =
        expectDestination(LLTAC_LDF, local?.let { "local_${it}_" } ?: "var", type, owner, Variable(name, type))

    fun expectStruct(type: TypeSign, vararg fields: Variable) {
        expectInstruction(LLTAC_STRUCT_DEF, TypeConst(type))
        fields.forEach { expectInstruction(LLTAC_FIELD, it) }
        expectInstruction(LLTAC_STRUCT_DEF_END, null)
    }

    /** Variant order defines the tags. Payload fields follow their constructor argument order. */
    fun expectSum(type: TypeSign, vararg variants: Pair<String, List<Variable>>) {
        val fields = variants.flatMap { (name, payload) ->
            payload.map { Variable("${name}_${it.name}", it.type) }
        }
        expectStruct(type, Variable("tag", types.intType), *fields.toTypedArray())
        for ((tag, variant) in variants.withIndex()) {
            val (name, payload) = variant
            val constructor = Variable(name, type)
            expectInstruction(LLTAC_SUM_VARIANT, TypeConst(type), constructor, Operand.IntConst(tag.toLong()))
            for (field in payload) {
                expectInstruction(LLTAC_SUM_VARIANT_FIELD, TypeConst(type), constructor, Variable("${name}_${field.name}", field.type))
            }
        }
    }

    fun expectWhen(subject: Variable, vararg branches: Pair<Int, () -> Unit>) {
        require(branches.isNotEmpty())
        var end: LLTACLabel? = null
        for ((tag, body) in branches) {
            val actual = expectField(subject, "tag", types.intType)
            val next = expectJumpIfNot(compare(LLTAC_EQ, actual, intLiteral(tag)))
            body()
            end = expectJump(end)
            expectLabel(next)
        }
        expectLabel(requireNotNull(end))
    }

    fun expectIf(condition: Variable, thenBody: () -> Unit) {
        val end = expectJumpIfNot(condition)
        thenBody()
        expectLabel(end)
    }

    fun expectIf(condition: Variable, thenBody: () -> Unit, elseBody: () -> Unit) {
        val otherwise = expectJumpIfNot(condition)
        thenBody()
        val end = expectJump()
        expectLabel(otherwise)
        elseBody()
        expectLabel(end)
    }

    fun expectWhile(condition: () -> Variable, body: () -> Unit) {
        val head = expectLabel()
        val end = expectJumpIfNot(condition())
        body()
        expectJump(head)
        expectLabel(end)
    }

    fun increment(value: Variable) = expectInstruction(LLTAC_ADD_I32, value, value, intLiteral(1))

    fun forBody(counter: Variable, limit: Variable, body: () -> Unit) {
        expectWhile({ compare(LLTAC_LT, counter, limit) }) {
            body()
            increment(counter)
        }
    }

    fun forIndex(size: Variable, start: Int = 0, body: (Variable) -> Unit) {
        val i = intLiteral(start, "i")
        forBody(i, expectNamedAssign("\$_right_borderi", size)) { body(i) }
    }

    fun intArrayWithValues(name: String, vararg values: Int): Variable {
        val array = expectAlloc(name, types.intType, values.size)
        for ((i, value) in values.withIndex()) {
            val index = intLiteral(i)
            val item = if (value < 0) {
                expectNeg(expectAssign(Operand.IntConst(-value.toLong()), types.intType))
            } else {
                intLiteral(value)
            }
            expectInstruction(LLTAC_STX, array, index, item)
        }
        return array
    }

    fun decimal(value: Variable) = valueCall("intToString", types.stringType, value)

    fun join(left: Variable, right: Variable) = expectBinOp(LLTAC_ADD, "var", types.stringType, left, right)

    fun expectCallArgument(value: Operand.Variable) {
        expectInstruction(LLTACOperation.LLTAC_PARAM, value)
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

    fun expectUnitCall(name: String, argumentCount: Int) = expectDiscardedCall(name, types.unitType, argumentCount)

    fun valueCall(
        name: String,
        type: TypeSign,
        vararg args: Operand.Variable,
        local: String? = null,
    ): Operand.Variable {
        args.forEach { expectCallArgument(it) }
        return expectCall(name, type, args.size, local)
    }

    fun printLine(value: Operand.Variable) {
        expectCallArgument(value)
        expectUnitCall("println", 1)
    }

    fun unitCall(name: String, vararg args: Variable) {
        args.forEach { expectCallArgument(it) }
        expectUnitCall(name, args.size)
    }

    fun expectDiscardedCall(name: String, returnType: TypeSign, argumentCount: Int) {
        expectInstruction(
            LLTACOperation.LLTAC_CALL, null, Operand.FunctionCall(name, returnType), Operand.IntConst(argumentCount.toLong()),
        )
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
            if (namePrefix == "var") {
                assertTrue(destination.name !in temporaryNames, "Expected a fresh temporary, reused ${destination.name}")
            }
            destination
        }

    private fun assertGeneratedName(name: String, prefix: String) {
        if (!name.matches(Regex("${Regex.escape(prefix)}[0-9]+"))) {
            throw AssertionError("Expected generated name ${prefix}<id>, got $name")
        }
    }
}
