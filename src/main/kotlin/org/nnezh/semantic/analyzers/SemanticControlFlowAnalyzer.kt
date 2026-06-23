package org.nnezh.org.nnezh.semantic.analyzers

import arrow.core.Either
import arrow.core.left
import arrow.core.right
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
import org.nnezh.ast.StatementASTNode
import org.nnezh.ast.StringLiteralExpressionNode
import org.nnezh.ast.UnaryExpressionASTNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.ast.VariableInitializationASTNode
import org.nnezh.ast.WhileStatementASTNode
import org.nnezh.org.nnezh.ast.AstErrorFactory
import org.nnezh.org.nnezh.base.Type
import org.nnezh.org.nnezh.semantic.SemanticAnalyzer
import org.nnezh.org.nnezh.semantic.generic.SemanticError
import org.nnezh.org.nnezh.semantic.generic.SemanticErrorType
import org.nnezh.org.nnezh.semantic.generic.SemanticSubAnalyzer
import java.util.Collections.singletonList
import javax.naming.ldap.Control
import kotlin.compareTo

class SemanticControlFlowAnalyzer(private val functionRegistry: FunctionRegistry) {
    fun analyzeProgramASTNode(node: ProgramASTNode): List<SemanticError> {
        return node.functions.flatMap { analyzeDeclareFunctionASTNode(it) }
    }

    private fun analyzeDeclareFunctionASTNode(node: DeclareFunctionASTNode): List<SemanticError> {
        val expectedResultType = functionRegistry.getResultType(node.name, node.args.arguments.map { it.type })!!
        return analyzeOuterBlock(node.block, expectedResultType)
    }

    private fun analyzeOuterBlock(node: BlockASTNode, expectedResultType: Type): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()
        val terminatesStatements = mutableListOf<StatementASTNode>()
        node.statements.forEach { statement ->
            val pair = statementTerminatesBlock(statement)
            errors.addAll(pair.first)
            if (pair.second) {
                terminatesStatements.add(statement)
            }
        }
        if (errors.any { it.isCriticalError }) {
            return errors
        }
        if (terminatesStatements.size == 1) {
            return if (expectedResultType == Type.UnitType) {
                errors
            } else {
                if ((terminatesStatements.first() as ReturnFunctionStatementASTNode).explicit) {
                    errors
                } else {
                    errors + singletonList(
                        SemanticError.ControlFlowSemanticError(
                            where = node,
                            critical = true,
                            errorType = SemanticErrorType.EXPLICIT_RETURN_IS_EXPECTED
                        )
                    )

                }
            }
        }
        if (terminatesStatements.size > 1) {
            val explicitReturn = terminatesStatements.first { statement ->
                ((statement as? ReturnFunctionStatementASTNode)?.explicit ?: false) || statement is IfStatementASTNode
            }
            return if (explicitReturn == node.statements.last { statement ->
                    (statement !is ReturnFunctionStatementASTNode || statement.explicit)
                }
            ) {
                errors
            } else {
                errors + singletonList(
                    SemanticError.ControlFlowSemanticError(
                        where = node,
                        critical = false,
                        errorType = SemanticErrorType.BLOCK_CONTAINS_CODE_AFTER_RETURN
                    )
                )
            }
        }

        return emptyList()
    }

    private fun checkIfStatementTerminatesBlockInAllBranches(ifStatement: IfStatementASTNode): Pair<List<SemanticError>, Boolean> {
        val thenBlockTerminates = analyzeInnerBlockASTNode(ifStatement.thenBlock)
        if (!thenBlockTerminates.second) {
            return thenBlockTerminates
        }
        ifStatement.elseBlock.fold(
            ifLeft = { elseBlock ->
                val res = analyzeInnerBlockASTNode(elseBlock)
                return Pair(thenBlockTerminates.first + res.first, res.second)
            },
            ifRight = { return Pair(thenBlockTerminates.first, false) }
        )
    }

    fun analyzeInnerBlockASTNode(node: BlockASTNode): Pair<List<SemanticError>, Boolean> {
        val errors = mutableListOf<SemanticError>()
        val terminatesStatements = mutableListOf<StatementASTNode>()
        node.statements.forEach { statement ->
            val pair = statementTerminatesBlock(statement)
            errors.addAll(pair.first)
            if (pair.second) {
                terminatesStatements.add(statement)
            }
        }
        if (terminatesStatements.isEmpty()) {
            return Pair(errors, false)
        }
        if (terminatesStatements.size > 1) {
            return Pair(
                errors + singletonList(
                    SemanticError.ControlFlowSemanticError(
                        where = node,
                        critical = true,
                        errorType = SemanticErrorType.BLOCK_CONTAINS_MORE_THAN_ONE_RETURN
                    )
                ), true
            )
        }
        val onlyTerminateStatement = terminatesStatements.first()
        if (onlyTerminateStatement != node.statements.last()) {
            return Pair(
                errors + singletonList(
                    SemanticError.ControlFlowSemanticError(
                        where = node,
                        critical = false,
                        errorType = SemanticErrorType.BLOCK_CONTAINS_CODE_AFTER_RETURN
                    )
                ), true
            )
        }
        return Pair(errors, true)

    }

    private fun statementTerminatesBlock(
        node: StatementASTNode
    ): Pair<List<SemanticError>, Boolean> {
        return when (node) {
            is AssignmentStatementASTNode -> Pair(listOf(), false)
            is CallFunctionStatementASTNode -> Pair(listOf(), false)
            is ForStatementASTNode -> Pair(listOf(), false)
            is IfStatementASTNode -> checkIfStatementTerminatesBlockInAllBranches(node)
            is ReturnFunctionStatementASTNode -> Pair(listOf(), true)
            is VariableInitializationASTNode -> Pair(listOf(), false)
            is WhileStatementASTNode -> Pair(listOf(), false)
        }
    }
}
