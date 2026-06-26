package org.nnezh.org.nnezh.ICGenerator

import org.nnezh.org.nnezh.base.Type

interface LeftValue {
    fun asLeftValue(): String
}
interface RightValue {
    fun asRightValue(): String
}

sealed class Operand {
    abstract val type: Type

    data class Variable(
        val name: String,
        override val type: Type
    ) : Operand(), LeftValue, RightValue {
        override fun toString() = "${name}:${type.name}"
        override fun asLeftValue(): String = name
        override fun asRightValue(): String = name
    }

    data class TypeConst(override val type: Type): Operand() {
        override fun toString(): String = type.name
    }

    data class IntConst(
        val value: Long
    ) : Operand(), RightValue {
        override val type: Type = Type.IntType
        override fun toString() = "${value}:Int"
        override fun asRightValue(): String = value.toString()
    }

    data class BoolConst(
        val value: Boolean
    ) : Operand(), RightValue {
        override val type: Type = Type.BoolType
        override fun toString() = "${value}:Bool"
        override fun asRightValue(): String = if (value) "true" else "false"
    }

    data class StringConst(
        val value: String
    ) : Operand(), RightValue {
        override val type: Type = Type.StringType
        override fun toString() = "${value}:String"
        override fun asRightValue(): String = "\"${value}\""
    }

    data class DoubleConst(
        val value: Double
    ) : Operand(), RightValue {
        override val type: Type = Type.DoubleType
        override fun toString() = "${value}:Double"
        override fun asRightValue(): String = value.toString()
    }

    data class FunctionCall(
        val name: String,
        override val type: Type,
    ): Operand() {
        override fun toString() = "${name}:${type.name}"
    }

    data class Label(
        val label: LLTACLabel,
    ): Operand() {
        override val type: Type
            get() = Type.UnitType
        override fun toString() = "${label.name}"
    }

}