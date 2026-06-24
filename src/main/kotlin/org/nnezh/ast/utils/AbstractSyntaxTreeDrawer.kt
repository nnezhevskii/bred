package org.nnezh.org.nnezh.ast.utils

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
import org.nnezh.ast.ImmutableVariableInitializationASTNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.MutableVariableInitializationASTNode
import org.nnezh.ast.ProgramASTNode
import org.nnezh.ast.ReturnFunctionStatementASTNode
import org.nnezh.ast.StaticArrayInitializationExpressionsList
import org.nnezh.ast.StringLiteralExpressionNode
import org.nnezh.ast.UnaryExpressionASTNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.ast.VariableInitializationASTNode
import org.nnezh.ast.WhileStatementASTNode
import org.nnezh.org.nnezh.base.Type

class AbstractSyntaxTreeDrawer {
    private val indent = " ".repeat(1)
    fun draw(root: ASTNode): String {
        val lines = recursive(root, "")
        return lines.joinToString("\n")
    }

    private fun recursive(
        node: ASTNode,
        shift: String = ""): List<String> {

        val acc: MutableList<String> = ArrayList()
        when (node) {
            is ProgramASTNode -> {
                acc.add("Program:")
                val globalVariables = node.globalVariables.map { recursive(it, shift + indent.repeat(2)) }
                val functions = node.functions.map { recursive(it, shift + indent.repeat(2)) }
                acc.add("${shift + indent}GlobalVariables:")
                acc.addAll(globalVariables.flatten())
                acc.add("${shift + indent}Functions:")
                acc.addAll(functions.flatten())
            }
            is BlockASTNode -> {
                acc.add("${shift}Block:")
                acc.addAll(node.statements.flatMap { recursive(it, shift + indent) })
            }
            is DeclareFunctionASTNode -> {
                val name = "${shift}func: ${node.name} returns ${Type.toString(node.resultType)}"
                val args = node.args.arguments.flatMap { recursive(it, shift + indent.repeat(1)) }
                val body = recursive(node.block, shift + indent.repeat(1))

                acc.add(name)
                acc.add("${shift}Arguments:")
                acc.addAll(args)
                acc.add("${shift}Body:")
                acc.addAll(body)

            }
            is BinaryExpressionASTNode -> {
                acc.add("${shift}BinaryExpression: ${node.operator.lexeme}")
                acc.add("${shift+indent}Left: ")
                acc.addAll(recursive(node.left, shift + indent.repeat(1)))
                acc.add("${shift+indent}Right: ")
                acc.addAll(recursive(node.right, shift + indent.repeat(1)))
            }
            is FunctionCallExpressionNode -> {
                acc.add("${shift}FunctionCall: ${node.name.lexeme}")
                acc.add("${shift+indent}with arguments:")
                acc.addAll(node.arguments.flatMap { recursive(it, shift + indent.repeat(1)) })
            }
            is BooleanLiteralExpressionNode -> {
                acc.add("${shift}BooleanLiteral:${node.value}")
            }
            is DoubleLiteralExpressionNode -> {
                acc.add("${shift}DoubleLiteral:${node.value}")
            }
            is IntLiteralExpressionNode -> {
                acc.add("${shift}IntLiteral:${node.value}")
            }
            is StringLiteralExpressionNode -> {
                acc.add("${shift}StringLiteral:${node.value}")
            }
            is UnaryExpressionASTNode -> {
                acc.add("UnaryExpression:${node.operator.lexeme}")
                acc.add("${indent}operator:${node.operator.lexeme}")
                acc.add("${indent}Operand:")
                acc.addAll(recursive(node.operand, shift + indent.repeat(1)))
            }
            is VariableExpressionNode -> {
                acc.add("${shift}Variable:${node.token.lexeme}")
            }
            is FunctionArgumentASTNode -> {
                acc.add("${shift}Argument: ${node.name} : ${node.type}")
            }
            is ImmutableVariableInitializationASTNode -> {
                acc.add("${shift + indent}ImmutableVariableInitialization:")
                acc.add("${shift + indent}Name: ${node.name}: ${Type.toString(node.type)}")
                acc.addAll(recursive(node.value, shift + indent.repeat(1)))

            }
            is EmptyNode -> acc.add("${indent}<Empty>")
            is AssignmentStatementASTNode -> {
                acc.add("${shift}Assign")
                TODO()
//                acc.add("${shift + indent}${node.name}")
//                acc.addAll(recursive(node.value, shift + indent.repeat(1)))
            }

            is IfStatementASTNode -> {
                acc.add("${shift}{If")
                acc.add("${shift+indent}Condition:")
                acc.addAll(recursive(node.condition, shift + indent.repeat(2)))
                acc.add("${shift+indent}Then:")
                acc.addAll(recursive(node.thenBlock, shift + indent.repeat(2)))
                acc.add("${shift+indent}Else:")
                acc.addAll(node.elseBlock.fold(
                    { emptyNode -> recursive(emptyNode, shift + indent.repeat(2)) },
                    { elseBlock -> recursive(elseBlock, shift + indent.repeat(2)) }
                ))
            }

            is WhileStatementASTNode -> {
                acc.add("${shift}While:")
                acc.add("${shift+indent}Condition:")
                acc.addAll(recursive(node.condition, shift + indent.repeat(2)))
                acc.add("${shift+indent}Body:")
                acc.addAll(recursive(node.bodyBlock, shift + indent.repeat(2)))
            }

            is MutableVariableInitializationASTNode -> {
                acc.add("${shift}MutableVariableInitialization:")
                acc.add("${shift + indent}Name: ${node.name}: ${node.type}")
                acc.addAll(recursive(node.value, shift + indent.repeat(1)))
            }

            is VariableInitializationASTNode -> {
                acc.add("${shift}MutableVariableInitialization:")
                acc.add("${shift + indent}Name: ${node.variableName}: ${node.variableType}")
                acc.addAll(recursive(node.valExpression, shift + indent.repeat(1)))
            }
            is ForStatementASTNode -> {
                acc.add("${shift}ForStatement:")
                acc.addAll(recursive(node.desugaredContent, shift + indent.repeat(1)))
            }

            is CallFunctionStatementASTNode -> {
                acc.add("${shift}Calling function")
                acc.addAll(recursive(node.expression, shift + indent))
            }

            is ReturnFunctionStatementASTNode -> {
                acc.add("${shift}Return: ")
                node.expression.fold(
                    ifLeft = { acc.add("${shift + indent}${Type.toString(it)}") },
                    ifRight = { acc.addAll(recursive(it, shift + indent.repeat(1))) },
                )
            }

            is StaticArrayInitializationExpressionsList -> {
                acc.add("${shift}Array<}")
//                acc.addAll(recursive(node.expression, shift + indent))
//                TODO()
            }
        }
        return acc
    }
}