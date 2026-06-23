package org.nnezh.org.nnezh.ICGenerator

import org.nnezh.org.nnezh.base.Type

sealed class Operand {
    abstract val type: Type

    data class Variable(
        val name: String,
        override val type: Type
    ) : Operand() {
        override fun toString() = "${name}:${type.name}"
    }

    data class IntConst(
        val value: Long
    ) : Operand() {
        override val type: Type = Type.IntType
        override fun toString() = "${value}:Int"
    }

    data class BoolConst(
        val value: Boolean
    ) : Operand() {
        override val type: Type = Type.BoolType
        override fun toString() = "${value}:Bool"
    }

    data class StringConst(
        val value: String
    ) : Operand() {
        override val type: Type = Type.StringType
        override fun toString() = "${value}:String"
    }

    data class DoubleConst(
        val value: Double
    ) : Operand() {
        override val type: Type = Type.DoubleType
        override fun toString() = "${value}:Double"
    }

    data class FunctionCall(
        val name: String,
        override val type: Type,
    ): Operand() {
        override fun toString() = "${name}:${type.name}"
    }

}