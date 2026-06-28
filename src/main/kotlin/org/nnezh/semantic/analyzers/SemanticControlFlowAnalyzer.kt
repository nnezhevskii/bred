package org.nnezh.org.nnezh.semantic.analyzers

import org.nnezh.bred.ast.AssignmentStatementASTNode
import org.nnezh.bred.ast.BlockASTNode
import org.nnezh.bred.ast.CallFunctionStatementASTNode
import org.nnezh.bred.ast.DeclareFunctionASTNode
import org.nnezh.bred.ast.ForStatementASTNode
import org.nnezh.bred.ast.IfStatementASTNode
import org.nnezh.bred.ast.ProgramASTNode
import org.nnezh.bred.ast.ReturnFunctionStatementASTNode
import org.nnezh.bred.ast.StatementASTNode
import org.nnezh.bred.ast.VariableInitializationASTNode
import org.nnezh.bred.ast.WhileStatementASTNode
import org.nnezh.org.nnezh.base.Type
import org.nnezh.org.nnezh.semantic.generic.SemanticError
import org.nnezh.org.nnezh.semantic.generic.SemanticErrorType
import java.util.Collections.singletonList

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
