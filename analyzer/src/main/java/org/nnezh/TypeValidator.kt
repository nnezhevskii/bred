package org.nnezh

import org.nnezh.bred.ast.BinaryOperator
import org.nnezh.bred.ast.TypeSign
import org.nnezh.bred.ast.UnaryOperator
import org.nnezh.bred.context.ProgramGlobalContext

class TypeValidator(private val globalContext: ProgramGlobalContext) {
    
    val IntType = globalContext.types["Int"]!!
    val DoubleType = globalContext.types["Double"]!!
    val StringType = globalContext.types["String"]!!
    val BoolType = globalContext.types["Boolean"]!!
    
    fun check(typeA: TypeSign, typeB: TypeSign): Boolean {
        return typeA == typeB
    }

    fun checkUnaryOperation(operation: UnaryOperator, type: TypeSign): Boolean {
        return when (operation) {
            UnaryOperator.Minus -> type == IntType || type == DoubleType
            UnaryOperator.Not -> type == BoolType
        }
    }

    fun produceUnaryType(operation: UnaryOperator, operandType: TypeSign): TypeSign? {
        return when (operation) {
            UnaryOperator.Minus -> when (operandType) {
                IntType, DoubleType -> operandType
                else -> null
            }
            UnaryOperator.Not -> if (operandType == BoolType) BoolType else null
        }
    }

    fun produceBinaryType(operation: BinaryOperator, typeA: TypeSign, typeB: TypeSign): TypeSign? {

        return when (operation) {
            BinaryOperator.Eq, BinaryOperator.Neq -> { if (typeA == typeB) BoolType else null }
            BinaryOperator.Plus -> { if (typeA in setOf(IntType, DoubleType, StringType) && typeA == typeB) typeA else null }
            BinaryOperator.Minus, BinaryOperator.Star, BinaryOperator.Slash -> {
                if (typeA in setOf(IntType, DoubleType) && typeA == typeB) typeA else null
            }
            BinaryOperator.Percent -> { if (typeA == typeB && typeA == IntType) IntType else null }

            BinaryOperator.And, BinaryOperator.Or -> if (typeA == typeB && typeA == BoolType) BoolType else null

            BinaryOperator.Lt, BinaryOperator.Gt, BinaryOperator.Le, BinaryOperator.Ge -> {
                if (typeA in setOf(IntType, DoubleType) && typeB in setOf(IntType, DoubleType) ||
                    (typeA == StringType && typeB == typeA)) BoolType else null
            }
        }
    }
}