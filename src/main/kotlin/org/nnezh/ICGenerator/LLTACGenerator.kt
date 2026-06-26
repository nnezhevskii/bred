package org.nnezh.org.nnezh.ICGenerator

import arrow.core.flatMap
import org.nnezh.ast.ASTNode
import org.nnezh.ast.ArrayAccessExpressionASTNode
import org.nnezh.ast.AssignmentStatementASTNode
import org.nnezh.ast.BinaryExpressionASTNode
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.BooleanLiteralExpressionNode
import org.nnezh.ast.CallFunctionStatementASTNode
import org.nnezh.ast.DeclareFunctionASTNode
import org.nnezh.ast.DoubleLiteralExpressionNode
import org.nnezh.ast.EmptyNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.ForStatementASTNode
import org.nnezh.ast.FunctionArgumentASTNode
import org.nnezh.ast.FunctionCallExpressionNode
import org.nnezh.ast.IfStatementASTNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.LiteralExpressionNode
import org.nnezh.ast.ProgramASTNode
import org.nnezh.ast.ReturnFunctionStatementASTNode
import org.nnezh.ast.StaticArrayExpressionNode
import org.nnezh.ast.StaticArrayInitializationExpressionsListNode
import org.nnezh.ast.StringLiteralExpressionNode
import org.nnezh.ast.UnaryExpressionASTNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.ast.VariableInitializationASTNode
import org.nnezh.ast.WhileStatementASTNode
import org.nnezh.org.nnezh.base.Type
import org.nnezh.org.nnezh.semantic.analyzers.ASTNodeTypeTable
import org.nnezh.org.nnezh.semantic.analyzers.FunctionRegistry
import java.util.Stack

class LLTACGenerator(
    private val nameEmitter: TACNameEmitter = TACNameEmitter(),
    private val typeTable: ASTNodeTypeTable,
    private val functionRegistry: FunctionRegistry
) {
    private val expressionLLTACGenerator = LLTACExpressionSubgenerator(nameEmitter, typeTable, functionRegistry)
//    private val stack = Stack<LLTACExpressionSubgeneratorResult>()

    fun build(root: ASTNode): List<LLTACElement> {
        val instructions = mutableListOf<LLTACElement>()
        instructions.addAll(visit(root))
        return instructions
    }


    fun visit(node: ASTNode): List<LLTACElement> {
        val instructions = mutableListOf<LLTACElement>()
        when (node) {
            is ProgramASTNode -> {
                // TODO: global variables

                node.functions.forEach {
                    instructions.addAll(visit(it))
                }
            }

            is DeclareFunctionASTNode -> {
                if (node.name != "main") {
                    val funcNameMangling = node.name + "_" +
                            (node.args.arguments
                                .map { arg -> arg.type })
                                .joinToString (separator = "_")
                    instructions.add(LLTACElement.function(funcNameMangling, node.resultType))
                } else {
                    instructions.add(LLTACElement.function(node.name, node.resultType))
                }

                instructions.addAll(node.args.arguments.map { LLTACElement.getParam(it.name, it.type) })
                instructions.addAll(visit(node.block))
            }

            is BlockASTNode -> {
                node.statements.forEach {
                    instructions.addAll(visit(it))
                }
            }

            is ExpressionASTNode -> {
                TODO()
            }

            is AssignmentStatementASTNode -> {
                when (node.lValue) {
                    is ArrayAccessExpressionASTNode -> {
                        val arr = node.lValue.array

                        val indexName = nameEmitter.nextVar()

                        val index = expressionLLTACGenerator.buildInstructionsForExpression(
                            indexName,
                            Type.IntType,
                            node.lValue.index
                        )
                        instructions.addAll(index.instructions)
                        if (indexName != index.finalVariable!!) {
                            instructions.add(LLTACElement.assignVariable(indexName, Type.IntType, index.finalVariable))
                        }

                        val rightValueName = nameEmitter.nextVar()
                        val rightValue = expressionLLTACGenerator.buildInstructionsForExpression(
                            rightValueName,
                            typeTable.get(node.rValue)!!,
                            node.rValue
                        )
                        instructions.addAll(rightValue.instructions)

                        instructions.add(
                            LLTACElement.store(
                                arr,
                                indexName,
                                rightValue.finalVariable!!,
                                typeTable.get(node.rValue)!!
                            )
                        )

                    }

                    is VariableExpressionNode -> {
                        val varName: String = node.lValue.token.lexeme
                        val varType = typeTable.get(node.lValue)!!

                        val res = expressionLLTACGenerator.buildInstructionsForExpression(varName, varType, node.rValue)
                        instructions.addAll(res.instructions)
                        if (varName != res.finalVariable) {
                            res.finalVariable?.let {
                                instructions.add(LLTACElement.assignVariable(varName, varType, it))
                            }
                        }
                    }

                    else -> {
                        TODO()
                    }
                }
            }

            is CallFunctionStatementASTNode -> {
                val res = expressionLLTACGenerator.buildInstructionsForExpression(
                    null,
                    Type.UnitType,
                    node.expression
                ).instructions
                instructions.addAll(res)
            }

            is ForStatementASTNode -> {
                instructions.addAll(visit(node.desugaredContent))
            }

            is IfStatementASTNode -> {
                val condVal = nameEmitter.nextVar()

                val res = expressionLLTACGenerator
                    .buildInstructionsForExpression(condVal, Type.BoolType, node.condition)
                instructions.addAll(res.instructions)

                val ifNotLabel = LLTACElement.label(nameEmitter.nextLbl()) as LLTACLabel
                instructions.add(LLTACElement.jumpIfNot(ifNotLabel, res.finalVariable!!))
                val then = visit(node.thenBlock)
                instructions.addAll(then)

                node.elseBlock.fold(
                    ifLeft = {
                        val label = nameEmitter.nextLbl()
                        val endOfThenLabel = LLTACElement.label(label) as LLTACLabel
                        instructions.add(LLTACElement.jump(endOfThenLabel))
                        instructions.add(ifNotLabel)
                        instructions.addAll(visit(it))
                        instructions.add(endOfThenLabel)
                    },
                    ifRight = {
                        instructions.add(ifNotLabel)
                    }
                )


            }

            is ReturnFunctionStatementASTNode -> {

                node.expression.fold(
                    ifLeft = {
                        instructions.add(LLTACElement.ret())
                    },
                    ifRight = {
                        val variable = nameEmitter.nextVar()
                        val type = typeTable.get(it)!!
                        val res = expressionLLTACGenerator.buildInstructionsForExpression(variable, type, it)
                        instructions.addAll(res.instructions)
                        instructions.add(LLTACElement.ret(res.finalVariable!!, res.finalType))
                    }
                )
            }

            is VariableInitializationASTNode -> {
                if (node.valExpression is LiteralExpressionNode) {
                    val instruction: LLTACElement = when (node.valExpression as LiteralExpressionNode) {
                        is BooleanLiteralExpressionNode -> {
                            LLTACElement.assign(
                                node.variableName,
                                node.variableType,
                                (node.valExpression as BooleanLiteralExpressionNode).value
                            )
                        }

                        is DoubleLiteralExpressionNode -> {
                            LLTACElement.assign(
                                node.variableName,
                                node.variableType,
                                (node.valExpression as DoubleLiteralExpressionNode).value
                            )
                        }

                        is IntLiteralExpressionNode -> {
                            LLTACElement.assign(
                                node.variableName,
                                node.variableType,
                                (node.valExpression as IntLiteralExpressionNode).value
                            )
                        }

                        is StringLiteralExpressionNode -> {
                            LLTACElement.assign(
                                node.variableName,
                                node.variableType,
                                (node.valExpression as StringLiteralExpressionNode).value
                            )
                        }
                    }
                    instructions.add(instruction)
                } else if (node is StaticArrayExpressionNode) {
                    instructions.add(
                        LLTACElement.alloc(
                            node.variableName,
                            (node.variableType as Type.StaticArrayType).elementType,
                            node.size
                        )
                    )
                    (node.valExpression)?.values?.forEachIndexed { index, value ->
                        val tmpVariable = nameEmitter.nextVar()

                        val indexName = nameEmitter.nextVar()

                        val res = expressionLLTACGenerator.buildInstructionsForExpression(
                            tmpVariable,
                            node.variableType,
                            value
                        )
                        val indexV = LLTACElement.assign(indexName, Type.IntType, index.toLong())
                        instructions.add(indexV)
                        instructions.addAll(res.instructions)
                        instructions.add(
                            LLTACElement.store(
                                node.variableName,
                                indexName,
                                res.finalVariable!!,
                                node.variableType
                            )
                        )
                    }
                } else {
                    if (node.valExpression != null) {
                        val res = expressionLLTACGenerator.buildInstructionsForExpression(
                            node.variableName,
                            node.variableType,
                            node.valExpression!!
                        )
                        instructions.addAll(res.instructions)
                        if (node.variableName != res.finalVariable) {
                            instructions.add(
                                LLTACElement.assignVariable(node.variableName, node.variableType, res.finalVariable!!)
                            )
                        }
                    }

                }

            }

            is WhileStatementASTNode -> {
                val beginOfWhile = LLTACElement.label(nameEmitter.nextLbl()) as LLTACLabel
                val endOfWhile = LLTACElement.label(nameEmitter.nextLbl()) as LLTACLabel
                instructions.add(beginOfWhile)

                val res = expressionLLTACGenerator
                    .buildInstructionsForExpression(nameEmitter.nextVar(), Type.BoolType, node.condition)
                instructions.addAll(res.instructions)

                instructions.add(LLTACElement.jumpIfNot(endOfWhile, res.finalVariable!!))
                instructions.addAll(visit(node.bodyBlock))
                instructions.add(LLTACElement.jump(beginOfWhile))
                instructions.add(endOfWhile)
            }

            is EmptyNode -> {}
            is FunctionArgumentASTNode -> {}

        }
        return instructions
    }

}


class TACNameEmitter() {
    private var varCounter = 1
    private var lblCounter = 1

    fun nextVar(): String = "var${varCounter++}"
    fun nextLbl(): String = "lbl${lblCounter++}"

}