package org.nnezh.org.nnezh.ICGenerator

import org.nnezh.ast.ASTNode
import org.nnezh.ast.AssignmentStatementASTNode
import org.nnezh.ast.BinaryExpressionASTNode
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.CallFunctionStatementASTNode
import org.nnezh.ast.DeclareFunctionASTNode
import org.nnezh.ast.EmptyNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.ForStatementASTNode
import org.nnezh.ast.FunctionArgumentASTNode
import org.nnezh.ast.FunctionCallExpressionNode
import org.nnezh.ast.IfStatementASTNode
import org.nnezh.ast.ProgramASTNode
import org.nnezh.ast.ReturnFunctionStatementASTNode
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
                instructions.add(LLTACElement.function(node.name))
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
                val varName = node.name
                val varType = typeTable.get(node.value)!!

                instructions.addAll(
                    expressionLLTACGenerator.buildInstructionsForExpression(varName, varType, node.value).instructions
                )


            }
            is CallFunctionStatementASTNode -> {
                val res = expressionLLTACGenerator.buildInstructionsForExpression(
                    null,
                    Type.UnitType,
                    node.expression
                ).instructions
                instructions.addAll(res)
            }
            is ForStatementASTNode -> TODO()
            is IfStatementASTNode -> TODO()
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
                val res = expressionLLTACGenerator.buildInstructionsForExpression(
                    node.variableName,
                    node.variableType,
                    node.valExpression)
                instructions.addAll(res.instructions)

            }
            is WhileStatementASTNode -> TODO()

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