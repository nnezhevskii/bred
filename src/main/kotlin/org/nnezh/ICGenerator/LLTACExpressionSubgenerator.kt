package org.nnezh.org.nnezh.ICGenerator

import arrow.core.left
import org.nnezh.ast.BinaryExpressionASTNode
import org.nnezh.ast.BinaryOperator
import org.nnezh.ast.BooleanLiteralExpressionNode
import org.nnezh.ast.DoubleLiteralExpressionNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.FunctionCallExpressionNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.LiteralExpressionNode
import org.nnezh.ast.StaticArrayInitializationExpressionsList
import org.nnezh.ast.StringLiteralExpressionNode
import org.nnezh.ast.UnaryExpressionASTNode
import org.nnezh.ast.UnaryOperator
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.org.nnezh.ICGenerator.LLTACElement.Companion.binOp
import org.nnezh.org.nnezh.base.Type
import org.nnezh.org.nnezh.semantic.analyzers.ASTNodeTypeTable
import org.nnezh.org.nnezh.semantic.analyzers.FunctionRegistry
import kotlin.math.exp

data class LLTACExpressionSubgeneratorResult(
    val instructions: List<LLTACElement>,
    val finalVariable: String?,
    val finalType: Type)

class LLTACExpressionSubgenerator(
    private val nameEmitter: TACNameEmitter,
    private val typeTable: ASTNodeTypeTable,
    private val functionTable: FunctionRegistry
) {

    val binaryOperatorToTACCommand = mapOf<BinaryOperator, LLTACOperation>(
        BinaryOperator.Plus to LLTACOperation.LLTAC_ADD,
        BinaryOperator.Minus to LLTACOperation.LLTAC_SUB,
        BinaryOperator.Star to LLTACOperation.LLTAC_MUL,
        BinaryOperator.Slash to LLTACOperation.LLTAC_DIV,
        BinaryOperator.Percent to LLTACOperation.LLTAC_MOD,
        BinaryOperator.Eq to LLTACOperation.LLTAC_EQ,
        BinaryOperator.Neq to LLTACOperation.LLTAC_NEQ,
        BinaryOperator.Lt to LLTACOperation.LLTAC_LT,
        BinaryOperator.Gt to LLTACOperation.LLTAC_GT,
        BinaryOperator.Le to LLTACOperation.LLTAC_LE,
        BinaryOperator.Ge to LLTACOperation.LLTAC_GE,
        BinaryOperator.And to LLTACOperation.LLTAC_AND,
        BinaryOperator.Or to LLTACOperation.LLTAC_OR,
    )
    val unaryOperatorToTACCommand = mapOf<UnaryOperator, LLTACOperation>(
        UnaryOperator.Not to LLTACOperation.LLTAC_NOT,
        UnaryOperator.Minus to LLTACOperation.LLTAC_MIN,
    )

    fun buildInstructionsForExpression(
        variable: String?,
        type: Type,
        expression: ExpressionASTNode): LLTACExpressionSubgeneratorResult {
        when (expression) {
            is BooleanLiteralExpressionNode -> {
//                TODO()
                val instructions: List<LLTACElement> = listOf(
                    LLTACElement.assign(variable!!, Type.BoolType, expression.value),
                )
                return LLTACExpressionSubgeneratorResult(instructions, variable, type!!)
            }

            is DoubleLiteralExpressionNode -> {
//                TODO()
                val instructions: List<LLTACElement> = listOf(
                    LLTACElement.assign(variable!!, type!!, expression.value),
                )
                return LLTACExpressionSubgeneratorResult(instructions, variable, Type.DoubleType)

            }

            is IntLiteralExpressionNode -> {
                val instructions: List<LLTACElement> = listOf(
                    LLTACElement.assign(variable!!, type!!, expression.value),
                )
                return LLTACExpressionSubgeneratorResult(instructions, variable, Type.IntType)
//                TODO()
            }
            is StringLiteralExpressionNode -> {
                val instructions: List<LLTACElement> = listOf(
                    LLTACElement.assign(variable!!, type!!, expression.value),
                )
                return LLTACExpressionSubgeneratorResult(instructions, variable, Type.StringType)
//                TODO()
            }


            is BinaryExpressionASTNode -> {
                val instructionsList = mutableListOf<LLTACElement>()

                val leftOperand = buildInstructionsForExpression(
                    nameEmitter.nextVar(),
                    typeTable.get(expression.left)!!,
                    expression.left
                )

                instructionsList.addAll(leftOperand.instructions)

                val rightOperand = buildInstructionsForExpression(
                    nameEmitter.nextVar(),
                    typeTable.get(expression.right)!!,
                    expression.right
                )
                instructionsList.addAll(rightOperand.instructions)

                val operation = binaryOperatorToTACCommand[expression.operator.kind]!!
                val varName =  variable //nameEmitter.nextVar()
                val varType = type // typeTable.get(expression)!!
                instructionsList.add(
                    binOp(
                        opcode = operation,
                        varName = varName!!,
                        varType = varType!!,
                        arg1Name = leftOperand.finalVariable!!,
                        arg1Type = leftOperand.finalType,
                        arg2Name = rightOperand.finalVariable!!,
                        arg2Type = rightOperand.finalType,
                    )
                )

                return LLTACExpressionSubgeneratorResult(
                    instructions = instructionsList,
                    finalVariable = varName,
                    finalType = varType,
                )
            }
            is FunctionCallExpressionNode -> {
                val arguments = expression.arguments.map { argument ->
                    buildInstructionsForExpression(
                        variable = nameEmitter.nextVar(),
                        type = typeTable.get(argument)!!,
                        argument)
                }

                val instructions: MutableList<LLTACElement> = arguments.flatMap { it.instructions}.toMutableList()
                arguments.forEach { argument ->
                    instructions.add(LLTACElement.param(argument.finalVariable!!, argument.finalType))
                }

                val resVar = variable //nameEmitter.nextVar()
                val resType = type
//                    functionTable.getResultType(
//                    expression.name.lexeme,
//                    expression.arguments.map { typeTable.get(it)!! }
//                )!!
                instructions.add(LLTACElement.call(
                    funName = expression.name.lexeme,
                    resVariable = resVar,
                    resType = resType!!,
                    amountOfArgs = arguments.count()))

                return LLTACExpressionSubgeneratorResult(
                    instructions = instructions,
                    finalVariable = resVar,
                    finalType = resType,
                )

            }
            is UnaryExpressionASTNode -> {
                val res = buildInstructionsForExpression(
                    variable = nameEmitter.nextVar(),
                    typeTable.get(expression)!!,
                    expression.operand)
                val opcode = unaryOperatorToTACCommand[expression.operator.kind]!!

                val instructions = mutableListOf<LLTACElement>()
                instructions.addAll(res.instructions)


                instructions.add(LLTACElement.unOp(
                    opcode = opcode,
                    varName = variable!!,
                    varType = type,
                    arg1Name = res.finalVariable!!,
                    arg1Type = res.finalType
                ))

                return LLTACExpressionSubgeneratorResult(
                    instructions = instructions,
                    finalVariable = variable,
                    finalType = type)
            }
            is VariableExpressionNode -> {
                return LLTACExpressionSubgeneratorResult(
                    instructions = listOf(),
                    finalVariable = expression.token.lexeme,
                    finalType = typeTable.get(expression)!!)

            }

            is StaticArrayInitializationExpressionsList -> TODO()
        }
    }
}