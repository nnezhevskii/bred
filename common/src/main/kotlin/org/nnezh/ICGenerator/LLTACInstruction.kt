package org.nnezh.org.nnezh.ICGenerator

import org.nnezh.bred.common.TypeNames
import org.nnezh.bred.common.TypeSign

sealed interface LLTACElement {
    companion object {
        fun function(name: String, type: TypeSign): LLTACElement =
            LLTACFunc(name, type)

        fun label(name: String): LLTACElement =
            LLTACLabel(name)

        fun getParam(name: String, type: TypeSign): LLTACElement =
            LLTACInstruction(
                opcode = LLTACOperation.LLTAC_GET_PARAM,
                destination = Operand.Variable(name = name, type = type),
            )

        fun alloc(dest: String, arrayType: TypeSign, size: Int): LLTACElement {
            require(arrayType.isArray) { "ALLOC expects array type, got ${arrayType.render()}" }
            return LLTACInstruction(
                opcode = LLTACOperation.LLTAC_ALLOC,
                destination = Operand.Variable(dest, arrayType),
                arg1 = Operand.TypeConst(arrayType.elementTypeOrNull!!),
                arg2 = Operand.IntConst(size.toLong()),
            )
        }

        fun globalArray(name: String, arrayType: TypeSign, size: Int, initialValues: List<Operand>?): LLTACElement {
            require(arrayType.isArray) { "global array expects array type, got ${arrayType.render()}" }
            return LLTACGlobalStaticArray(
                name = name,
                type = arrayType,
                size = size,
                initialValues = initialValues,
            )
        }

        fun param(name: String, type: TypeSign): LLTACElement =
            LLTACInstruction(
                opcode = LLTACOperation.LLTAC_PARAM,
                destination = Operand.Variable(name = name, type = type),
            )

        fun jumpIfNot(label: LLTACLabel, cond: String, booleanType: TypeSign): LLTACElement =
            LLTACInstruction(
                opcode = LLTACOperation.LLTAC_JMP_IF_NOT,
                destination = Operand.Label(label),
                arg1 = Operand.Variable(cond, booleanType),
            )

        fun jump(label: LLTACLabel): LLTACElement =
            LLTACInstruction(
                opcode = LLTACOperation.LLTAC_JMP,
                destination = Operand.Label(label),
            )

        fun store(array: String, index: String, source: String, arrayType: TypeSign, elementType: TypeSign, intType: TypeSign): LLTACElement =
            LLTACInstruction(
                opcode = LLTACOperation.LLTAC_STX,
                destination = Operand.Variable(name = array, type = arrayType),
                arg1 = Operand.Variable(index, type = intType),
                arg2 = Operand.Variable(name = source, type = elementType),
            )

        fun load(array: String, destination: String, index: String, arrayType: TypeSign, elementType: TypeSign, intType: TypeSign): LLTACElement =
            LLTACInstruction(
                opcode = LLTACOperation.LLTAC_LDX,
                destination = Operand.Variable(name = destination, type = elementType),
                arg1 = Operand.Variable(name = array, arrayType),
                arg2 = Operand.Variable(index, intType),
            )

        fun assignVariable(name: String, type: TypeSign, arg: String): LLTACElement =
            LLTACInstruction(
                opcode = LLTACOperation.LLTAC_ASSIGN,
                destination = Operand.Variable(name = name, type = type),
                arg1 = Operand.Variable(name = arg, type = type),
            )

        fun assign(name: String, type: TypeSign, value: String): LLTACElement =
            LLTACInstruction(
                opcode = LLTACOperation.LLTAC_ASSIGN,
                destination = Operand.Variable(name = name, type = type),
                arg1 = Operand.StringConst(value),
            )

        fun assign(name: String, type: TypeSign, value: Long): LLTACElement =
            LLTACInstruction(
                opcode = LLTACOperation.LLTAC_ASSIGN,
                destination = Operand.Variable(name = name, type = type),
                arg1 = if (type.isPrimitive && type.name == TypeNames.LONG) {
                    Operand.LongConst(value)
                } else {
                    Operand.IntConst(value)
                },
            )

        fun assign(name: String, type: TypeSign, value: Double): LLTACElement =
            LLTACInstruction(
                opcode = LLTACOperation.LLTAC_ASSIGN,
                destination = Operand.Variable(name = name, type = type),
                arg1 = Operand.DoubleConst(value),
            )

        fun assign(name: String, type: TypeSign, value: Boolean): LLTACElement =
            LLTACInstruction(
                opcode = LLTACOperation.LLTAC_ASSIGN,
                destination = Operand.Variable(name = name, type = type),
                arg1 = Operand.BoolConst(value),
            )

        fun ret(name: String? = null, type: TypeSign? = null): LLTACElement =
            LLTACRetInstruction(if (name == null || type == null) null else Pair(name, type))

        fun binOp(
            opcode: LLTACOperation,
            varName: String,
            varType: TypeSign,
            arg1Name: String,
            arg1Type: TypeSign,
            arg2Name: String,
            arg2Type: TypeSign,
        ): LLTACElement =
            LLTACInstruction(
                opcode = opcode,
                destination = Operand.Variable(name = varName, type = varType),
                arg1 = Operand.Variable(arg1Name, arg1Type),
                arg2 = Operand.Variable(arg2Name, arg2Type),
            )

        fun unOp(
            opcode: LLTACOperation,
            varName: String,
            varType: TypeSign,
            arg1Name: String,
            arg1Type: TypeSign,
        ): LLTACElement =
            LLTACInstruction(
                opcode = opcode,
                destination = Operand.Variable(name = varName, type = varType),
                arg1 = Operand.Variable(arg1Name, arg1Type),
            )

        fun call(funName: String, resVariable: String?, resType: TypeSign, amountOfArgs: Int): LLTACElement =
            LLTACInstruction(
                opcode = LLTACOperation.LLTAC_CALL,
                destination = resVariable?.let { Operand.Variable(name = it, type = resType) },
                arg1 = Operand.FunctionCall(funName, resType),
                arg2 = Operand.IntConst(amountOfArgs.toLong()),
            )
    }
}

data class LLTACInstruction(
    val opcode: LLTACOperation,
    val destination: Operand?,
    val arg1: Operand? = null,
    val arg2: Operand? = null,
): LLTACElement {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("${opcode} ")
        destination?.let { sb.append(" ${it}") }
        arg1?.let { operand -> sb.append(" $operand") }
        arg2?.let { operand -> sb.append(" $operand") }
        return sb.toString()
    }
}

data class LLTACRetInstruction(
    val value: Pair<String, TypeSign>?,
): LLTACElement {
    val opcode: LLTACOperation = LLTACOperation.LLTAC_RET
    override fun toString(): String =
        value?.let { "${opcode} ${it.first}:${it.second.render()}" } ?: "${opcode} Unit"
}

data class LLTACFunc(val name: String, val type: TypeSign): LLTACElement {
    override fun toString() = "func $name"
}

data class LLTACLabel(val name: String): LLTACElement {
    override fun toString() = "$name"
}

data class LLTACGlobalStaticArray(
    val name: String,
    val type: TypeSign,
    val size: Int,
    val initialValues: List<Operand>?,
) : LLTACElement {
    override fun toString(): String {
        val initializer = initialValues?.joinToString(prefix = " [", postfix = "]") { it.toString() }.orEmpty()
        return "global $name:${type.name}[$size]$initializer"
    }
}
