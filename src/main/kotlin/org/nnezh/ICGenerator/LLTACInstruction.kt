package org.nnezh.org.nnezh.ICGenerator

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.nnezh.org.nnezh.base.Type

sealed interface LLTACElement {
    companion object {
        fun function(name: String, type: Type): LLTACElement {
            return LLTACFunc(name, type)
        }
        fun label(name: String): LLTACElement {
            return LLTACLabel(name)
        }
        fun getParam(name: String, type: Type): LLTACElement {
            return LLTACInstruction(
                opcode = LLTACOperation.LLTAC_GET_PARAM,
                destination = Operand.Variable(name = name, type = type),
            )
        }
        fun alloc(dest: String, type: Type.StaticArrayType, size: Int): LLTACElement {
            return LLTACInstruction(
                opcode = LLTACOperation.LLTAC_ALLOC,
                destination = Operand.Variable(dest, type),
                arg1 = Operand.TypeConst(type.elementType),
                arg2 = Operand.IntConst(size.toLong()),
            )
        }

        fun param(name: String, type: Type): LLTACElement {
            return LLTACInstruction(
                opcode = LLTACOperation.LLTAC_PARAM,
                destination = Operand.Variable(name = name, type = type),
            )
        }

        fun jumpIfNot(label: LLTACLabel, cond: String): LLTACElement {
            return LLTACInstruction(
                opcode = LLTACOperation.LLTAC_JMP_IF_NOT,
                destination = Operand.Label(label),
                arg1 = Operand.Variable(cond, Type.BoolType)
            )
        }

        fun jump(label: LLTACLabel): LLTACElement {
            return LLTACInstruction(
                opcode = LLTACOperation.LLTAC_JMP,
                destination = Operand.Label(label)
            )
        }

        //STORE  dest, offset, source
        fun store(array: String, index: String, source: String, type: Type): LLTACElement {
            return LLTACInstruction(
                opcode = LLTACOperation.LLTAC_STX,
                destination = Operand.Variable(name = array, type = type),
                arg1 = Operand.Variable(index, type = Type.IntType),
                arg2 = Operand.Variable(name = source, type = type),
            )
        }

        fun load(array: String, destination: String, index: String, type: Type): LLTACElement {
            return LLTACInstruction(
                opcode = LLTACOperation.LLTAC_LDX,
                destination = Operand.Variable(name = destination, type = type),
                arg1 = Operand.Variable(name = array, Type.StaticArrayType(type, 0)),
                arg2 = Operand.Variable(index, Type.IntType),
            )
        }

        fun assignVariable (name: String, type: Type, arg: String): LLTACElement {
            return LLTACInstruction(
                opcode = LLTACOperation.LLTAC_ASSIGN,
                destination = Operand.Variable(name = name, type = type),
                arg1 = Operand.Variable(name = arg, type = type),
            )
        }

        fun assign (name: String, type: Type, value: String): LLTACElement {
            return LLTACInstruction(
                opcode = LLTACOperation.LLTAC_ASSIGN,
                destination = Operand.Variable(name = name, type = type),
                arg1 = Operand.StringConst(value)
            )
        }
        fun assign (name: String, type: Type, value: Long): LLTACElement {
            return LLTACInstruction(
                opcode = LLTACOperation.LLTAC_ASSIGN,
                destination = Operand.Variable(name = name, type = type),
                arg1 = Operand.IntConst(value)
            )
        }
        fun assign (name: String, type: Type, value: Double): LLTACElement {
            return LLTACInstruction(
                opcode = LLTACOperation.LLTAC_ASSIGN,
                destination = Operand.Variable(name = name, type = type),
                arg1 = Operand.DoubleConst(value)
            )
        }
        fun assign (name: String, type: Type, value: Boolean): LLTACElement {
            return LLTACInstruction(
                opcode = LLTACOperation.LLTAC_ASSIGN,
                destination = Operand.Variable(name = name, type = type),
                arg1 = Operand.BoolConst(value)
            )
        }

        fun ret(name: String? = null, type: Type? = null): LLTACElement {
            if (name == null || type == null) {
                return LLTACRetInstruction(Type.UnitType.left())
            }
            return LLTACRetInstruction(Pair(name, type).right())
        }

        fun binOp(opcode: LLTACOperation,
                  varName: String,
                  varType: Type,
                  arg1Name: String,
                  arg1Type: Type,
                  arg2Name: String,
                  arg2Type: Type,): LLTACElement {
            return LLTACInstruction(
                opcode = opcode,
                destination = Operand.Variable(name = varName, type = varType),
                arg1 = Operand.Variable(arg1Name, arg1Type),
                arg2 = Operand.Variable(arg2Name, arg2Type)
            )
        }

        fun unOp(opcode: LLTACOperation,
                  varName: String,
                  varType: Type,
                  arg1Name: String,
                  arg1Type: Type): LLTACElement {
            return LLTACInstruction(
                opcode = opcode,
                destination = Operand.Variable(name = varName, type = varType),
                arg1 = Operand.Variable(arg1Name, arg1Type)
            )
        }


        fun call(funName: String, resVariable: String?, resType: Type, amountOfArgs: Int): LLTACElement {
            return LLTACInstruction(
                opcode = LLTACOperation.LLTAC_CALL,
                destination =  resVariable?.let { Operand.Variable(name = it, type = resType) },
                arg1 = Operand.FunctionCall(funName, resType),
                arg2 = Operand.IntConst(amountOfArgs.toLong())
            )
        }
    }
}

data class LLTACInstruction(
    val opcode: LLTACOperation,
    val destination: Operand?,
    val arg1: Operand? = null,
    val arg2: Operand? = null
): LLTACElement {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("${opcode} ")
        destination?.let {" ${sb.append(it.toString())}" }
//        sb.append(" $destination")
        arg1?.let { operand ->
           sb.append(" $operand")
        }
        arg2?.let { operand ->
            sb.append(" $operand")
        }

        return sb.toString()
    }
}

data class LLTACRetInstruction(
    val value: Either<Type.UnitType, Pair<String, Type>>
): LLTACElement {
    val opcode: LLTACOperation = LLTACOperation.LLTAC_RET
    override fun toString(): String {
        val strValue = value.fold(
            ifLeft = { "${opcode} Unit" },
            ifRight = { "${opcode} ${it.first}:${it.second}" }
        )
        return strValue
    }

}

data class LLTACFunc(val name: String, val type: Type): LLTACElement {
    override fun toString() = "func $name"
}

data class LLTACLabel(val name: String): LLTACElement {
    override fun toString() = "$name"
}