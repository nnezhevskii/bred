package org.nnezh.org.nnezh.semantic.analyzers

import arrow.core.raise.nullable
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
import org.nnezh.org.nnezh.base.Type
import org.nnezh.org.nnezh.semantic.analyzers.VariableScopeSubAnalyzer.VariableDeclaration
import org.nnezh.org.nnezh.semantic.generic.SemanticError
import org.nnezh.org.nnezh.semantic.generic.SemanticErrorType
import org.nnezh.org.nnezh.semantic.generic.SemanticSubAnalyzer
import java.util.Collections.singletonList
import java.util.IdentityHashMap
import kotlin.collections.fold

data class Scope(
    val variablesTable: MutableMap<String, VariableDeclaration>,
    val baseScope: Scope?
) {
    fun lookUp(variableName: String): Pair<VariableDeclaration, Boolean>? = nullable {
        variablesTable[variableName]?.let { it to true }
            ?: baseScope?.lookUp(variableName)?.copy(second = false)
            ?:ensureNotNull(null)
    }
}


class VariableScopeSubAnalyzer: SemanticSubAnalyzer() {

    private var scope: Scope = Scope(
        variablesTable = HashMap(),
        baseScope = null
    )

    override fun analyzeProgramASTNode(node: ProgramASTNode): List<SemanticError> {
        node.globalVariables
            .map { v -> VariableDeclaration.build(v) }
            .associateBy { it.name }
            .forEach { (variable, declaration) -> scope.variablesTable[variable] = declaration }

        return node.functions.fold(
            mutableListOf(),
            operation = { lst, node -> (lst + analyzeDeclareFunctionASTNode(node)).toMutableList() }
        )
    }

    override fun analyzeBlockASTNode(node: BlockASTNode): List<SemanticError> {
        val listOfErrors = mutableListOf<SemanticError>()
        val extendedScope = Scope(
            variablesTable = mutableMapOf(),
            baseScope = scope,
        )
        scope = extendedScope

        node.statements.forEach { statement ->
            val errors = routeStatementHandling(statement)
            listOfErrors.addAll(errors)

            if (errors.any { err -> err.isCriticalError }) {
                scope = scope.baseScope!! // TODO?
                return listOfErrors
            }
        }

        scope = scope.baseScope!! // TODO?
        return listOfErrors
    }

    override fun analyzeDeclareFunctionASTNode(node: DeclareFunctionASTNode): List<SemanticError> {
        val listOfErrors = mutableListOf<SemanticError>()
        val args = node.args.arguments
            .map { argumentASTNode -> VariableDeclaration.build(argumentASTNode) }
            .associateBy { it.name }
            .toMutableMap()

        val overshadowVariables = args.values.map { arg -> arg.name }
            .filter { valName -> scope.lookUp(valName) != null }
        overshadowVariables.forEach { variable ->
            listOfErrors.add(
                SemanticError.VariableScopeSemanticError(
                    where = node.args.arguments.first { arg -> arg.name == variable },
                    critical = true,
                    errorType = SemanticErrorType.VARIABLE_OVERSHADOW
                )
            )
        }
        scope = Scope(
            baseScope = scope,
            variablesTable = args
        )
        listOfErrors.addAll(analyzeBlockASTNode(node.block))

        scope = scope.baseScope!! // TODO

        return listOfErrors
    }

    override fun analyzeEmptyNode(node: EmptyNode): List<SemanticError> {
       return emptyList()
    }

    override fun analyzeBinaryExpressionASTNode(node: BinaryExpressionASTNode): List<SemanticError> {
        return routeExpressionHandling(node.left) + routeExpressionHandling(node.right)
    }

    override fun analyzeFunctionCallExpressionNode(node: FunctionCallExpressionNode): List<SemanticError> {
        return node.arguments.fold(
            mutableListOf(),
            operation = { lst, node ->  (lst + routeExpressionHandling(node)).toMutableList() }
        )
    }

    override fun analyzeBooleanLiteralExpressionNode(node: BooleanLiteralExpressionNode): List<SemanticError> = emptyList()

    override fun analyzeDoubleLiteralExpressionNode(node: DoubleLiteralExpressionNode): List<SemanticError> = emptyList()

    override fun analyzeIntLiteralExpressionNode(node: IntLiteralExpressionNode): List<SemanticError> = emptyList()

    override fun analyzeStringLiteralExpressionNode(node: StringLiteralExpressionNode): List<SemanticError> = emptyList()

    override fun analyzeUnaryExpressionASTNode(node: UnaryExpressionASTNode): List<SemanticError> {
        return routeExpressionHandling(node.operand)
    }

    override fun analyzeVariableExpressionNode(node: VariableExpressionNode): List<SemanticError> {
        return if (scope.lookUp(node.token.lexeme) == null) {
            singletonList(SemanticError.VariableScopeSemanticError(
                where = node,
                critical = true,
                errorType = SemanticErrorType.UNKNOWN_VARIABLE))
        } else {
            emptyList()
        }
    }

    override fun analyzeFunctionArgumentASTNode(node: FunctionArgumentASTNode): List<SemanticError> {
        return emptyList()
    }

    override fun analyzeAssignmentStatementASTNode(node: AssignmentStatementASTNode): List<SemanticError> {
        //                // TODO: checking val/var
        val listOfErrors = mutableListOf<SemanticError>()
        if (scope.lookUp(node.name) == null) {
            listOfErrors.add(
                SemanticError.VariableScopeSemanticError(where = node,
                    errorType = SemanticErrorType.UNKNOWN_VARIABLE,
                    critical = true
                )
            )
        }
        listOfErrors.addAll(routeExpressionHandling(node.value))

        return listOfErrors
    }

    override fun analyzeCallFunctionStatementASTNode(node: CallFunctionStatementASTNode): List<SemanticError> {
        return routeExpressionHandling(node.expression)
    }

    override fun analyzeForStatementASTNode(node: ForStatementASTNode): List<SemanticError> {
        return analyzeBlockASTNode(node.desugaredContent)
    }

    override fun analyzeIfStatementASTNode(node: IfStatementASTNode): List<SemanticError> {
        val listOfErrors = mutableListOf<SemanticError>()
        listOfErrors.addAll(routeExpressionHandling(node.condition))
        if (listOfErrors.none { it.isCriticalError }) {
            listOfErrors.addAll(analyzeBlockASTNode(node.thenBlock))
        }
        if (listOfErrors.none { it.isCriticalError }) {
            node.elseBlock.fold(
                ifRight = {  },
                ifLeft = { listOfErrors.addAll(analyzeBlockASTNode(it)) })
        }
        return listOfErrors
    }

    override fun analyzeReturnFunctionStatementASTNode(node: ReturnFunctionStatementASTNode): List<SemanticError> {
        val res = mutableListOf<SemanticError>()
        node.expression.fold(
            ifLeft = {  },
            ifRight = { retExpression -> res.addAll(routeExpressionHandling(retExpression)) }
        )

        return res
    }

    override fun analyzeVariableInitializationASTNode(node: VariableInitializationASTNode): List<SemanticError> {
        val result = mutableListOf<SemanticError>()

        val variableFromScope = scope.lookUp(node.variableName)
        if (variableFromScope != null) {
            result.add(SemanticError.VariableScopeSemanticError(
                where = node,
                critical = true,
                errorType = if (variableFromScope.second) SemanticErrorType.VARIABLE_REDECLARATION else SemanticErrorType.VARIABLE_OVERSHADOW
            ))
        }

        result.addAll(routeExpressionHandling(node.valExpression))

        if (result.none { it.isCriticalError }) {
            val variable = VariableDeclaration.build(node)
            scope.variablesTable[variable.name] = variable
        }

        return result
    }

    override fun analyzeWhileStatementASTNode(node: WhileStatementASTNode): List<SemanticError> {
        val listOfErrors = mutableListOf<SemanticError>()
        listOfErrors.addAll(routeExpressionHandling(node.condition))
        if (listOfErrors.none { it.isCriticalError }) {
            listOfErrors.addAll(analyzeBlockASTNode(node.bodyBlock))
        }
        return listOfErrors
    }

    data class VariableDeclaration(
        val name: String,
        val type: Type,
        val isMutable: Boolean,
    ) {
        companion object {
            fun build(varASTNode: VariableInitializationASTNode): VariableDeclaration {
                return VariableDeclaration(
                    name = varASTNode.variableName,
                    type = varASTNode.variableType,
                    isMutable = varASTNode.isMutable
                )
            }

            fun build(argNode: FunctionArgumentASTNode): VariableDeclaration {
                return VariableDeclaration(
                    name = argNode.name,
                    type = argNode.type,
                    isMutable = false
                )
            }
        }
    }
}