package org.nnezh.org.nnezh.ICGenerator

import org.nnezh.ast.ASTNode
import org.nnezh.ast.AssignmentStatementASTNode
import org.nnezh.ast.BinaryExpressionASTNode
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.BooleanLiteralExpressionNode
import org.nnezh.ast.CallFunctionStatementASTNode
import org.nnezh.ast.DeclareFunctionASTNode
import org.nnezh.ast.DoubleLiteralExpressionNode
import org.nnezh.ast.EmptyNode
import org.nnezh.ast.ForStatementASTNode
import org.nnezh.ast.FunctionArgumentASTNode
import org.nnezh.ast.FunctionCallExpressionNode
import org.nnezh.ast.IfStatementASTNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.ProgramASTNode
import org.nnezh.ast.ReturnFunctionStatementASTNode
import org.nnezh.ast.StringLiteralExpressionNode
import org.nnezh.ast.UnaryExpressionASTNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.ast.VariableInitializationASTNode
import org.nnezh.ast.WhileStatementASTNode

class LLTACGenerator(
    private val nameEmitter: TACNameEmitter = TACNameEmitter()
) {
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

            is BlockASTNode -> TODO()
            is DeclareFunctionASTNode -> {
                instructions.add(LLTACElement.function(node.name))
            }
            is EmptyNode -> TODO()
            is BinaryExpressionASTNode -> TODO()
            is FunctionCallExpressionNode -> { TODO()
            }

            is BooleanLiteralExpressionNode -> TODO()
            is DoubleLiteralExpressionNode -> TODO()
            is IntLiteralExpressionNode -> TODO()
            is StringLiteralExpressionNode -> TODO()
            is UnaryExpressionASTNode -> TODO()
            is VariableExpressionNode -> TODO()
            is FunctionArgumentASTNode -> TODO()
            is AssignmentStatementASTNode -> TODO()
            is CallFunctionStatementASTNode -> TODO()
            is ForStatementASTNode -> TODO()
            is IfStatementASTNode -> TODO()
            is ReturnFunctionStatementASTNode -> TODO()
            is VariableInitializationASTNode -> TODO()
            is WhileStatementASTNode -> TODO()
        }
        return instructions
    }

}


class TACNameEmitter() {
    private var varCounter = 1
    private var lblCounter = 1

    private fun nextVar(): String = "var${varCounter++}"
    private fun nextLbl(): String = "lbl${lblCounter++}"

}