package org.nnezh.org.nnezh.ICGenerator

import org.nnezh.bred.common.TypeNames
import org.nnezh.bred.common.TypeSign

interface LeftValue {
    fun asLeftValue(): String
}

interface RightValue {
    fun asRightValue(): String
}

sealed class Operand {
    open val type: TypeSign? = null

    data class Variable(
        val name: String,
        override val type: TypeSign,
    ) : Operand(), LeftValue, RightValue {
        override fun toString() = "${name}:${type.name}"
        override fun asLeftValue(): String = name
        override fun asRightValue(): String = name
    }

    data class TypeConst(override val type: TypeSign): Operand() {
        override fun toString(): String = type.name
    }

    data class IntConst(
        val value: Long,
    ) : Operand(), RightValue {
        override fun toString() = "${value}:${TypeNames.INT}"
        override fun asRightValue(): String = value.toString()
    }

    data class LongConst(
        val value: Long,
    ) : Operand(), RightValue {
        override fun toString() = "${value}:${TypeNames.LONG}"
        override fun asRightValue(): String = "${value}LL"
    }

    data class BoolConst(
        val value: Boolean,
    ) : Operand(), RightValue {
        override fun toString() = "${value}:${TypeNames.BOOLEAN}"
        override fun asRightValue(): String = if (value) "true" else "false"
    }

    data class StringConst(
        val value: String,
    ) : Operand(), RightValue {
        override fun toString() = "${value}:${TypeNames.STRING}"
        override fun asRightValue(): String = value
    }

    data class DoubleConst(
        val value: Double,
    ) : Operand(), RightValue {
        override fun toString() = "${value}:${TypeNames.DOUBLE}"
        override fun asRightValue(): String = value.toString()
    }

    data class FunctionCall(
        val name: String,
        override val type: TypeSign,
    ): Operand() {
        override fun toString() = "${name}:${type.name}"
    }

    data class Label(
        val label: LLTACLabel,
    ): Operand() {
        override fun toString() = "${label.name}"
    }
}
