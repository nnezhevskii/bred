package org.nnezh.org.nnezh.semantic.generic

import org.nnezh.ast.ASTNode
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
import org.nnezh.ast.ProgramASTNode
import org.nnezh.ast.ReturnFunctionStatementASTNode
import org.nnezh.ast.StatementASTNode
import org.nnezh.ast.StringLiteralExpressionNode
import org.nnezh.ast.UnaryExpressionASTNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.ast.VariableInitializationASTNode
import org.nnezh.ast.WhileStatementASTNode

abstract class SemanticSubAnalyzer {
    operator fun invoke(root: ASTNode): List<SemanticError> {
        return when (root) {
            is BlockASTNode -> analyzeBlockASTNode(root)
            is DeclareFunctionASTNode -> analyzeDeclareFunctionASTNode(root)
            is EmptyNode -> analyzeEmptyNode(root)
            is BinaryExpressionASTNode -> analyzeBinaryExpressionASTNode(root)
            is FunctionCallExpressionNode -> analyzeFunctionCallExpressionNode(root)
            is BooleanLiteralExpressionNode -> analyzeBooleanLiteralExpressionNode(root)
            is DoubleLiteralExpressionNode -> analyzeDoubleLiteralExpressionNode(root)
            is IntLiteralExpressionNode -> analyzeIntLiteralExpressionNode(root)
            is StringLiteralExpressionNode -> analyzeStringLiteralExpressionNode(root)
            is UnaryExpressionASTNode -> analyzeUnaryExpressionASTNode(root)
            is VariableExpressionNode -> analyzeVariableExpressionNode(root)
            is FunctionArgumentASTNode -> analyzeFunctionArgumentASTNode(root)
            is ProgramASTNode -> analyzeProgramASTNode(root)
            is AssignmentStatementASTNode -> analyzeAssignmentStatementASTNode(root)
            is CallFunctionStatementASTNode -> analyzeCallFunctionStatementASTNode(root)
            is ForStatementASTNode -> analyzeForStatementASTNode(root)
            is IfStatementASTNode -> analyzeIfStatementASTNode(root)
            is ReturnFunctionStatementASTNode -> analyzeReturnFunctionStatementASTNode(root)
            is VariableInitializationASTNode -> analyzeVariableInitializationASTNode(root)
            is WhileStatementASTNode -> analyzeWhileStatementASTNode(root)
        }
    }

    protected fun routeStatementHandling(node: StatementASTNode): List<SemanticError> = when (node) {
        is AssignmentStatementASTNode -> analyzeAssignmentStatementASTNode(node)
        is CallFunctionStatementASTNode -> analyzeCallFunctionStatementASTNode(node)
        is ForStatementASTNode -> analyzeForStatementASTNode(node)
        is IfStatementASTNode -> analyzeIfStatementASTNode(node)
        is ReturnFunctionStatementASTNode -> analyzeReturnFunctionStatementASTNode(node)
        is VariableInitializationASTNode -> analyzeVariableInitializationASTNode(node)
        is WhileStatementASTNode -> analyzeWhileStatementASTNode(node)
    }

    protected fun routeExpressionHandling(node: ExpressionASTNode): List<SemanticError> = when (node) {
        is BinaryExpressionASTNode -> analyzeBinaryExpressionASTNode(node)
        is FunctionCallExpressionNode -> analyzeFunctionCallExpressionNode(node)
        is BooleanLiteralExpressionNode -> analyzeBooleanLiteralExpressionNode(node)
        is DoubleLiteralExpressionNode -> analyzeDoubleLiteralExpressionNode(node)
        is IntLiteralExpressionNode -> analyzeIntLiteralExpressionNode(node)
        is StringLiteralExpressionNode -> analyzeStringLiteralExpressionNode(node)
        is UnaryExpressionASTNode -> analyzeUnaryExpressionASTNode(node)
        is VariableExpressionNode -> analyzeVariableExpressionNode(node)
    }

    abstract fun analyzeBlockASTNode(node: BlockASTNode): List<SemanticError>
    abstract fun analyzeDeclareFunctionASTNode(node: DeclareFunctionASTNode): List<SemanticError>
    abstract fun analyzeEmptyNode(node: EmptyNode): List<SemanticError>
    abstract fun analyzeBinaryExpressionASTNode(node: BinaryExpressionASTNode): List<SemanticError>
    abstract fun analyzeFunctionCallExpressionNode(node: FunctionCallExpressionNode): List<SemanticError>
    abstract fun analyzeBooleanLiteralExpressionNode(node: BooleanLiteralExpressionNode): List<SemanticError>
    abstract fun analyzeDoubleLiteralExpressionNode(node: DoubleLiteralExpressionNode): List<SemanticError>
    abstract fun analyzeIntLiteralExpressionNode(node: IntLiteralExpressionNode): List<SemanticError>
    abstract fun analyzeStringLiteralExpressionNode(node: StringLiteralExpressionNode): List<SemanticError>
    abstract fun analyzeUnaryExpressionASTNode(node: UnaryExpressionASTNode): List<SemanticError>
    abstract fun analyzeVariableExpressionNode(node: VariableExpressionNode): List<SemanticError>
    abstract fun analyzeFunctionArgumentASTNode(node: FunctionArgumentASTNode): List<SemanticError>
    abstract fun analyzeProgramASTNode(node: ProgramASTNode): List<SemanticError>
    abstract fun analyzeAssignmentStatementASTNode(node: AssignmentStatementASTNode): List<SemanticError>
    abstract fun analyzeCallFunctionStatementASTNode(node: CallFunctionStatementASTNode): List<SemanticError>
    abstract fun analyzeForStatementASTNode(node: ForStatementASTNode): List<SemanticError>
    abstract fun analyzeIfStatementASTNode(node: IfStatementASTNode): List<SemanticError>
    abstract fun analyzeReturnFunctionStatementASTNode(node: ReturnFunctionStatementASTNode): List<SemanticError>
    abstract fun analyzeVariableInitializationASTNode(node: VariableInitializationASTNode): List<SemanticError>
    abstract fun analyzeWhileStatementASTNode(node: WhileStatementASTNode): List<SemanticError>
}