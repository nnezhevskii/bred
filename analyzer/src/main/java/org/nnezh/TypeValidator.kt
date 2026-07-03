package org.nnezh

import org.nnezh.bred.ast.BinaryOperator
import org.nnezh.bred.ast.UnaryOperator
import org.nnezh.bred.common.TypeSign
import org.nnezh.bred.context.ProgramGlobalContext

class TypeValidator(private val globalContext: ProgramGlobalContext) {

    private val intType = globalContext.intType
    private val longType = globalContext.longType
    private val doubleType = globalContext.doubleType
    private val stringType = globalContext.stringType
    private val booleanType = globalContext.booleanType
    private val numericTypes = setOf(intType, longType, doubleType)
    private val additiveTypes = numericTypes + stringType
    
    fun check(typeA: TypeSign, typeB: TypeSign): Boolean {
        return typeA == typeB
    }

    fun checkUnaryOperation(operation: UnaryOperator, type: TypeSign): Boolean {
        return when (operation) {
            UnaryOperator.Minus -> type in numericTypes
            UnaryOperator.Not -> type == booleanType
        }
    }

    fun produceUnaryType(operation: UnaryOperator, operandType: TypeSign): TypeSign? {
        return when (operation) {
            UnaryOperator.Minus -> when (operandType) {
                intType, longType, doubleType -> operandType
                else -> null
            }
            UnaryOperator.Not -> if (operandType == booleanType) booleanType else null
        }
    }

    fun produceBinaryType(operation: BinaryOperator, typeA: TypeSign, typeB: TypeSign): TypeSign? {

        return when (operation) {
            BinaryOperator.Eq, BinaryOperator.Neq -> { if (typeA == typeB) booleanType else null }
            BinaryOperator.Plus -> { if (typeA in additiveTypes && typeA == typeB) typeA else null }
            BinaryOperator.Minus, BinaryOperator.Star, BinaryOperator.Slash -> {
                if (typeA in numericTypes && typeA == typeB) typeA else null
            }
            BinaryOperator.Percent -> { if (typeA == typeB && typeA in setOf(intType, longType)) typeA else null }

            BinaryOperator.And, BinaryOperator.Or -> if (typeA == typeB && typeA == booleanType) booleanType else null

            BinaryOperator.Lt, BinaryOperator.Gt, BinaryOperator.Le, BinaryOperator.Ge -> {
                if (typeA in numericTypes && typeB in numericTypes ||
                    (typeA == stringType && typeB == typeA)) booleanType else null
            }
        }
    }
}
