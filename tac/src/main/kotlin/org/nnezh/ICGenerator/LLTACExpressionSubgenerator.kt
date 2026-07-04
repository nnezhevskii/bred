package org.nnezh.org.nnezh.ICGenerator

import arrow.core.raise.result
import org.nnezh.bred.ast.ArrayElementAccessASTNode
import org.nnezh.bred.ast.ArrayInitializationExpressionASTNode
import org.nnezh.bred.ast.BinaryExpressionASTNode
import org.nnezh.bred.ast.BinaryOperator
import org.nnezh.bred.ast.BooleanLiteralExpressionASTNode
import org.nnezh.bred.ast.DoubleLiteralExpressionASTNode
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.bred.ast.FieldAccessChainExpressionASTNode
import org.nnezh.bred.ast.FunctionCallExpressionASTNode
import org.nnezh.bred.ast.IntLiteralExpressionASTNode
import org.nnezh.bred.ast.LongLiteralExpressionASTNode
import org.nnezh.bred.ast.StringLiteralExpressionASTNode
import org.nnezh.bred.ast.UnaryExpressionASTNode
import org.nnezh.bred.ast.UnaryOperator
import org.nnezh.bred.ast.VariableExpressionASTNode
import org.nnezh.bred.common.TypeSign
import org.nnezh.bred.context.ProgramGlobalContext
import org.nnezh.org.nnezh.ICGenerator.LLTACElement.Companion.binOp

data class LLTACExpressionSubgeneratorResult(
    val instructions: List<LLTACElement>,
    val finalVariable: String?,
    val finalType: TypeSign,
)

class LLTACExpressionSubgenerator(
    private val nameEmitter: TACNameEmitter,
    private val typeTable: Map<ExpressionASTNode, TypeSign>,
    private val globalContext: ProgramGlobalContext,
    private val mangledFunctionsCallback: () -> Map<String, String>,
) {

    private val binaryOperatorToTACCommand = mapOf(
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

    private val unaryOperatorToTACCommand = mapOf(
        UnaryOperator.Not to LLTACOperation.LLTAC_NOT,
        UnaryOperator.Minus to LLTACOperation.LLTAC_NEG,
    )

    fun buildInstructionsForExpression(
        variable: String?,
        type: TypeSign,
        expression: ExpressionASTNode,
    ): LLTACExpressionSubgeneratorResult =
        when (expression) {
            is BooleanLiteralExpressionASTNode -> {
                val destination = variable ?: lltacError("literal TAC destination is required", expression)
                LLTACExpressionSubgeneratorResult(
                    instructions = listOf(LLTACElement.assign(destination, type, expression.value)),
                    finalVariable = destination,
                    finalType = type,
                )
            }

            is DoubleLiteralExpressionASTNode -> {
                val destination = variable ?: lltacError("literal TAC destination is required", expression)
                LLTACExpressionSubgeneratorResult(
                    instructions = listOf(LLTACElement.assign(destination, type, expression.value)),
                    finalVariable = destination,
                    finalType = type,
                )
            }

            is IntLiteralExpressionASTNode -> {
                val destination = variable ?: lltacError("literal TAC destination is required", expression)
                LLTACExpressionSubgeneratorResult(
                    instructions = listOf(LLTACElement.assign(destination, type, expression.value.toLong())),
                    finalVariable = destination,
                    finalType = type,
                )
            }

            is LongLiteralExpressionASTNode -> {
                val destination = variable ?: lltacError("literal TAC destination is required", expression)
                LLTACExpressionSubgeneratorResult(
                    instructions = listOf(LLTACElement.assign(destination, type, expression.value)),
                    finalVariable = destination,
                    finalType = type,
                )
            }

            is StringLiteralExpressionASTNode -> {
                val destination = variable ?: lltacError("literal TAC destination is required", expression)
                LLTACExpressionSubgeneratorResult(
                    instructions = listOf(LLTACElement.assign(destination, type, expression.value)),
                    finalVariable = destination,
                    finalType = type,
                )
            }

            is BinaryExpressionASTNode -> {
                val instructions = mutableListOf<LLTACElement>()
                val leftOperand = buildInstructionsForExpression(
                    variable = nameEmitter.nextVar(),
                    type = expression.left.tacType(),
                    expression = expression.left,
                )
                instructions.addAll(leftOperand.instructions)

                val rightOperand = buildInstructionsForExpression(
                    variable = nameEmitter.nextVar(),
                    type = expression.right.tacType(),
                    expression = expression.right,
                )
                instructions.addAll(rightOperand.instructions)

                val destination = variable ?: lltacError("binary expression TAC destination is required", expression)
                instructions.add(
                    binOp(
                        opcode = binaryOperatorToTACCommand.getValue(expression.operator.kind),
                        varName = destination,
                        varType = type,
                        arg1Name = leftOperand.finalVariable!!,
                        arg1Type = leftOperand.finalType,
                        arg2Name = rightOperand.finalVariable!!,
                        arg2Type = rightOperand.finalType,
                    )
                )

                LLTACExpressionSubgeneratorResult(instructions, destination, type)
            }

            is FunctionCallExpressionASTNode -> {
                val arguments = expression.arguments.map { argument ->
                    buildInstructionsForExpression(
                        variable = nameEmitter.nextVar(),
                        type = argument.tacType(),
                        expression = argument,
                    )
                }

                val instructions = arguments.flatMap { it.instructions }.toMutableList()
                arguments.forEach { argument ->
                    instructions.add(LLTACElement.param(argument.finalVariable!!, argument.finalType))
                }

                val resultVariable = variable
                instructions.add(
                    LLTACElement.call(
                        funName = mangledFunctionsCallback()[expression.name] ?: expression.name,
                        resVariable = resultVariable,
                        resType = type,
                        amountOfArgs = arguments.count(),
                    )
                )

                LLTACExpressionSubgeneratorResult(instructions, resultVariable, type)
            }

            is UnaryExpressionASTNode -> {
                val operand = buildInstructionsForExpression(
                    variable = nameEmitter.nextVar(),
                    type = expression.operand.tacType(),
                    expression = expression.operand,
                )
                val destination = variable ?: lltacError("unary expression TAC destination is required", expression)
                val instructions = operand.instructions.toMutableList()
                instructions.add(
                    LLTACElement.unOp(
                        opcode = unaryOperatorToTACCommand.getValue(expression.operator.kind),
                        varName = destination,
                        varType = type,
                        arg1Name = operand.finalVariable!!,
                        arg1Type = operand.finalType,
                    )
                )

                LLTACExpressionSubgeneratorResult(instructions, destination, type)
            }

            is VariableExpressionASTNode -> {
                LLTACExpressionSubgeneratorResult(
                    instructions = emptyList(),
                    finalVariable = expression.token.lexeme,
                    finalType = expression.tacType(),
                )
            }

            is ArrayInitializationExpressionASTNode -> {
                lltacError("array initializer should be lowered by ArrayDeclarationASTNode", expression)
            }

            is ArrayElementAccessASTNode -> {
                val instructions = mutableListOf<LLTACElement>()
                val index = buildInstructionsForExpression(
                    variable = nameEmitter.nextVar(),
                    type = globalContext.intType,
                    expression = expression.index,
                )
                instructions.addAll(index.instructions)

                val destination = nameEmitter.nextVar()
                val elementType = type
                val arrayType = globalContext.arrayOf(elementType)
                instructions.add(LLTACElement.assign(destination, elementType, 0))
                instructions.add(
                    LLTACElement.load(
                        array = expression.name,
                        destination = destination,
                        index = index.finalVariable!!,
                        arrayType = arrayType,
                        elementType = elementType,
                        intType = globalContext.intType,
                    )
                )

                LLTACExpressionSubgeneratorResult(
                    instructions = instructions,
                    finalVariable = destination,
                    finalType = elementType,
                )
            }

            is FieldAccessChainExpressionASTNode -> {
                expression as FieldAccessChainExpressionASTNode
                // TODO
                if (expression.next!!.next == null) {
                    return LLTACExpressionSubgeneratorResult(
                        instructions = emptyList(),
                        finalVariable = "${expression.root}.${expression.next!!.root}",
                        finalType = expression.tacType(),
                    )
                }

//                val res = buildInstructionsForExpression(expression.next!!, )
//                val instructions = mutableListOf<LLTACElement>()
//                instructions.add()
                TODO()
            }
        }

    private fun ExpressionASTNode.tacType(): TypeSign =
        typeTable[this] ?: inferLiteralTacType(this)

    private fun inferLiteralTacType(expression: ExpressionASTNode): TypeSign =
        when (expression) {
            is BooleanLiteralExpressionASTNode -> globalContext.booleanType
            is DoubleLiteralExpressionASTNode -> globalContext.doubleType
            is IntLiteralExpressionASTNode -> globalContext.intType
            is LongLiteralExpressionASTNode -> globalContext.longType
            is StringLiteralExpressionASTNode -> globalContext.stringType
            else -> lltacError("missing semantic type for expression", expression)
        }
}
