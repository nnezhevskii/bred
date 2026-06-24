package org.nnezh.org.nnezh.semantic.analyzers

import arrow.core.left
import com.sun.tools.javac.tree.TreeInfo.types
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles
import org.nnezh.ast.ASTNode
import org.nnezh.ast.ArrayAccessExpressionASTNode
import org.nnezh.ast.AssignmentStatementASTNode
import org.nnezh.ast.BinaryExpressionASTNode
import org.nnezh.ast.BinaryOperator
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
import org.nnezh.ast.StaticArrayExpressionNode
import org.nnezh.ast.StaticArrayInitializationExpressionsListNode
import org.nnezh.ast.StringLiteralExpressionNode
import org.nnezh.ast.UnaryExpressionASTNode
import org.nnezh.ast.UnaryOperator
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.ast.VariableInitializationASTNode
import org.nnezh.ast.WhileStatementASTNode
import org.nnezh.org.nnezh.base.Type
import org.nnezh.org.nnezh.semantic.generic.FunctionSignature
import org.nnezh.org.nnezh.semantic.generic.SemanticError
import org.nnezh.org.nnezh.semantic.generic.SemanticErrorType
import org.nnezh.org.nnezh.semantic.generic.SemanticSubAnalyzer
import java.util.Collections
import java.util.Collections.singletonList
import java.util.IdentityHashMap
import kotlin.math.exp


class ASTNodeTypeTable {
    private val types = IdentityHashMap<ASTNode, Type>()

    fun put(node: ExpressionASTNode, type: Type) { types[node] = type }
    fun get(node: ExpressionASTNode): Type? = types[node]
}

data class TypeScope(
    val parentScope: TypeScope?,
    val typeTable: ASTNodeTypeTable) {
//    private val typeTable = ASTNodeTypeTable()
    private val variablesType = mutableMapOf<String, Type>()

    fun put(node: ExpressionASTNode, type: Type) {
        typeTable.put(node, type)
    }
    fun put(variable: String, type: Type) {
        variablesType[variable] = type
    }
    fun get(node: ExpressionASTNode): Type? = typeTable.get(node) ?: parentScope?.get(node)
    fun get(variable: String): Type? = variablesType[variable] ?: parentScope?.get(variable)
}

class TypeValidator {
    fun check(typeA: Type, typeB: Type): Boolean {
        return typeA == typeB
    }

    fun checkUnaryOperation(operation: UnaryOperator, type: Type): Boolean {
        return when (operation) {
            UnaryOperator.Minus -> type == Type.IntType || type == Type.DoubleType
            UnaryOperator.Not -> type == Type.BoolType
        }
    }

    fun produceUnaryType(operation: UnaryOperator, operandType: Type): Type? {
        return when (operation) {
            UnaryOperator.Minus -> when (operandType) {
                Type.IntType, Type.DoubleType -> operandType
                else -> null
            }
            UnaryOperator.Not -> if (operandType == Type.BoolType) Type.BoolType else null
        }
    }

    fun produceBinaryType(operation: BinaryOperator, typeA: Type, typeB: Type): Type? {

        return when (operation) {
            BinaryOperator.Eq, BinaryOperator.Neq -> { if (typeA == typeB) Type.BoolType else null }
            BinaryOperator.Plus -> { if (typeA in setOf(Type.IntType, Type.DoubleType, Type.StringType) && typeA == typeB) typeA else null }
            BinaryOperator.Minus, BinaryOperator.Star, BinaryOperator.Slash -> {
                if (typeA in setOf(Type.IntType, Type.DoubleType) && typeA == typeB) typeA else null
            }
            BinaryOperator.Percent -> { if (typeA == typeB && typeA == Type.IntType) Type.IntType else null }

            BinaryOperator.And, BinaryOperator.Or -> if (typeA == typeB && typeA == Type.BoolType) Type.BoolType else null

            BinaryOperator.Lt, BinaryOperator.Gt, BinaryOperator.Le, BinaryOperator.Ge -> {
                if (typeA in setOf(Type.IntType, Type.DoubleType) && typeB in setOf(Type.IntType, Type.DoubleType) ||
                    (typeA == Type.StringType && typeB == typeA)) Type.BoolType else null
            }
        }
    }
}

class TypeChecker(
    private val functionRegistry: FunctionRegistry,
    private val typeValidator: TypeValidator,
    ): SemanticSubAnalyzer() {

    private val typeTable = ASTNodeTypeTable()
    var typeScope = TypeScope(
        parentScope = null,
        typeTable = typeTable,
    )

    override fun analyzeProgramASTNode(node: ProgramASTNode): List<SemanticError> {
        typeScope = TypeScope(null, typeTable = typeTable)
        val varErrors = node.globalVariables.flatMap { node -> analyzeVariableInitializationASTNode(node) }
        val funErrors = node.functions.flatMap { node -> analyzeDeclareFunctionASTNode(node) }

        return varErrors + funErrors
    }

    override fun analyzeFunctionCallExpressionNode(node: FunctionCallExpressionNode): List<SemanticError> {
        val errors = node.arguments.flatMap { expr -> routeExpressionHandling(expr) }.toMutableList()
        if (errors.any { it.isCriticalError }) {
            return errors
        }
        val functionName = node.name.lexeme
        val args = node.arguments.map { expr -> typeScope.get(expr)!! }
        if (!functionRegistry.isFunctionRegistered(functionName, args)) {
            errors.add(SemanticError.TypeSemanticError(node, true, errorType = SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES))
        }
        if (errors.none { it.isCriticalError }) {
            typeScope.put(node, functionRegistry.getResultType(functionName, args)!!)
        }


        return errors
    }

    override fun analyzeBinaryExpressionASTNode(node: BinaryExpressionASTNode): List<SemanticError> {
        val leftErrors = routeExpressionHandling(node.left)
        if (leftErrors.any { it.isCriticalError }) {
            return leftErrors
        }

        val rightErrors = routeExpressionHandling(node.right)
        if (rightErrors.any { it.isCriticalError }) {
            return rightErrors
        }

        val leftType = typeScope.get(node.left)!!
        val rightType = typeScope.get(node.right)!!

        val errors = (leftErrors + rightErrors).toMutableList()
        val binaryOperationType = typeValidator.produceBinaryType(node.operator.kind, leftType, rightType)
        if ( binaryOperationType == null) {
            errors.add(SemanticError.TypeSemanticError(node, true, errorType = SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES))
        } else {
            typeScope.put(node, binaryOperationType)
        }
        return errors
    }

    override fun analyzeReturnFunctionStatementASTNode(node: ReturnFunctionStatementASTNode): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()
        node.expression.fold(
            ifLeft = { it },
            ifRight = { expr ->
                errors.addAll(routeExpressionHandling(expr))
                if (errors.any { it.isCriticalError }) {
                    return errors
                }
                if (currentFunctionSignatureType!! != typeScope.get(expr)!!) {
                    return errors + singletonList(
                        SemanticError.TypeSemanticError(
                            where = node,
                            critical = true,
                            errorType = SemanticErrorType.METHOD_HAS_WRONG_RETURN
                        )
                    )
                }

                typeScope.put(expr, typeScope.get(expr)!!) // << TODO: непонятное выражение, разобрать

            },
        )


        return errors
    }

    private var currentFunctionSignatureType: Type? = null
    override fun analyzeDeclareFunctionASTNode(node: DeclareFunctionASTNode): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()
        errors.addAll(node.args.arguments.flatMap { arg ->
            analyzeFunctionArgumentASTNode(arg)
        })

        if (errors.any { it.isCriticalError }) {
            return errors
        }

        currentFunctionSignatureType = functionRegistry.getResultType(node.name, node.args.arguments.map { it.type })!!
        errors.addAll(analyzeBlockASTNode(node.block))
        if (errors.any { it.isCriticalError }) {
            return errors
        }

        val retStatementType = findActualReturnValueOfFunction(node)
        val explicit = (node.block.statements
            .first { statement -> statement is ReturnFunctionStatementASTNode } as ReturnFunctionStatementASTNode).explicit

        if (explicit && !typeValidator.check(node.resultType, retStatementType)) {
            errors.add(SemanticError.TypeSemanticError(node, true, errorType = SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES))
        }

        currentFunctionSignatureType = null
        return errors
    }

    override fun analyzeBlockASTNode(node: BlockASTNode): List<SemanticError> {
        typeScope = TypeScope(typeScope, typeTable = typeTable)

        node.statements.forEach { statement ->
            val foundErrors = routeStatementHandling(statement)

            if (foundErrors.any { it.isCriticalError }) {
                return foundErrors
            }
        }

        typeScope = typeScope.parentScope!!
        return emptyList()
    }


    override fun analyzeEmptyNode(node: EmptyNode): List<SemanticError> {
        return emptyList()
    }

    override fun analyzeUnaryExpressionASTNode(node: UnaryExpressionASTNode): List<SemanticError> {
        val errors = routeExpressionHandling(node.operand).toMutableList()
        if (errors.any { it.isCriticalError }) {
            return errors
        }

        val operandType = typeScope.get(node.operand)!!

        if (!typeValidator.checkUnaryOperation(node.operator.kind, operandType)) {
            errors.add(SemanticError.TypeSemanticError(node, true, errorType = SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES))
        } else {
            val unaryResultType = typeValidator.produceUnaryType(node.operator.kind, operandType)
            if (unaryResultType == null) {
                errors.add(SemanticError.TypeSemanticError(node, true, errorType = SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES))
            } else {
                typeScope.put(node, unaryResultType)
            }
        }

        return errors
    }

    override fun analyzeFunctionArgumentASTNode(node: FunctionArgumentASTNode): List<SemanticError> {
        typeScope.put(node.name, node.type)
        return emptyList()
    }

    override fun analyzeVariableExpressionNode(node: VariableExpressionNode): List<SemanticError> {
        val type = typeScope.get(node.token.lexeme)
        if (type == null) {
            return singletonList(SemanticError.VariableScopeSemanticError(
                where = node,
                critical = true,
                errorType = SemanticErrorType.UNKNOWN_VARIABLE))
        } else {
            typeScope.put(node, type)
        }

        return emptyList()
    }

    override fun analyzeAssignmentStatementASTNode(node: AssignmentStatementASTNode): List<SemanticError> {
        val errors = routeExpressionHandling(node.lValue).toMutableList()
        errors.addAll(routeExpressionHandling(node.rValue))
        if (errors.any { it.isCriticalError }) {
            return errors
        }
        val lValueType: Type = if (typeScope.get(node.lValue) == null) {
            if (node.lValue is ArrayAccessExpressionASTNode) {
                typeScope.get(node.lValue.array)!!
            } else {
                return singletonList(SemanticError.TypeSemanticError(node, true, errorType = SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES))
            }
        } else {
            typeScope.get(node.lValue)!!
        }

        val rValueType = typeScope.get(node.rValue)!!

        if (!typeValidator.check(lValueType, rValueType)) {
            errors.add(SemanticError.TypeSemanticError(node, true, errorType = SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES))
        }

        return errors
    }

    override fun analyzeCallFunctionStatementASTNode(node: CallFunctionStatementASTNode): List<SemanticError> {
        return routeExpressionHandling(node.expression)
    }

    override fun analyzeForStatementASTNode(node: ForStatementASTNode): List<SemanticError> {
        return analyzeBlockASTNode(node.desugaredContent)
    }

    override fun analyzeWhileStatementASTNode(node: WhileStatementASTNode): List<SemanticError> {
        val errors = routeExpressionHandling(node.condition).toMutableList()
        if (!typeValidator.check(typeScope.get(node.condition)!! , Type.BoolType)) {
            errors.add(SemanticError.TypeSemanticError(node, true, errorType = SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES))
        }
        errors.addAll(analyzeBlockASTNode(node.bodyBlock))

        return errors
    }

    override fun analyzeStaticArrayInitializationExpressionsList(node: StaticArrayInitializationExpressionsListNode): List<SemanticError> {
        val errors = node.values.flatMap { routeExpressionHandling(it) }.toMutableList()
        if (errors.any { it.isCriticalError }) {
            return errors
        }
        val arrayTypes = node.values.map { typeScope.get(it)!! }.toSet()
        if (arrayTypes.size > 1) {
            errors.add(SemanticError.TypeSemanticError(node, true, errorType = SemanticErrorType.TYPE_CHECKER_INCONSISTENT_ARRAY_TYPE))
            return errors
        }
        typeScope.put(node, arrayTypes.first())

        return errors
    }

    override fun analyzeArrayAccessExpressionASTNode(node: ArrayAccessExpressionASTNode): List<SemanticError> {
        val errors = routeExpressionHandling(node.index).toMutableList()
        if (errors.any { it.isCriticalError }) {
            return errors
        }

        if (typeScope.get(node.index) != Type.IntType) {
            errors.add(SemanticError.TypeSemanticError(node, true, errorType = SemanticErrorType.ARRAY_INDEX_IS_NOT_INTEGER))
        }
        typeScope.put(node, typeScope.get(node.array)!!)
        return errors
    }

    override fun analyzeIfStatementASTNode(node: IfStatementASTNode): List<SemanticError> {
        val errors = routeExpressionHandling(node.condition).toMutableList()
        if (errors.any { it.isCriticalError }) {
            return errors
        }

        if (!typeValidator.check(typeScope.get(node.condition)!! , Type.BoolType)) {
            errors.add(SemanticError.TypeSemanticError(node, true, errorType = SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES))
        }
        if (errors.any { it.isCriticalError }) {
            return errors
        }
        errors.addAll(analyzeBlockASTNode(node.thenBlock))
        if (errors.any { it.isCriticalError }) {
            return errors
        }
        node.elseBlock.fold(
            ifLeft = { errors.addAll(analyzeBlockASTNode(it)) },
            ifRight = {}
        )

        return errors
    }

    override fun analyzeVariableInitializationASTNode(node: VariableInitializationASTNode): List<SemanticError> {
        typeScope.put(node.variableName, node.variableType)
        val errors = (node.valExpression?.let { routeExpressionHandling(it ) } ?: emptyList()) .toMutableList()
        if (errors.any { it.isCriticalError }) {
            return errors
        }

        // TODO: check nullability
        node.valExpression?.let {
            if (!typeValidator.check(node.variableType, typeScope.get(it)!!)) {
                errors.add(SemanticError.TypeSemanticError(node, true, errorType = SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES))
            }
        }


        node.valExpression?.let {
            if (node is StaticArrayExpressionNode) {
                if (node.size != node.valExpression!!.values.size) {
                    errors.add(SemanticError.TypeSemanticError(node, true, errorType = SemanticErrorType.INVALID_AMOUNT_OF_ARGUMENTS_IN_ARRAYS_INITIALIZATION))
                }
            }

        }

        return errors
    }

    override fun analyzeBooleanLiteralExpressionNode(node: BooleanLiteralExpressionNode): List<SemanticError> {
        typeScope.put(node, Type.BoolType)
        return emptyList()
    }

    override fun analyzeDoubleLiteralExpressionNode(node: DoubleLiteralExpressionNode): List<SemanticError> {
        typeScope.put(node, Type.DoubleType)
        return emptyList()
    }

    override fun analyzeIntLiteralExpressionNode(node: IntLiteralExpressionNode): List<SemanticError> {
        typeScope.put(node, Type.IntType)
        return emptyList()
    }

    override fun analyzeStringLiteralExpressionNode(node: StringLiteralExpressionNode): List<SemanticError> {
        typeScope.put(node, Type.StringType)
        return emptyList()
    }


    private fun findActualReturnValueOfFunction(node: DeclareFunctionASTNode): Type {
        val returnStatement = node.block.statements
            .first { statement -> statement is ReturnFunctionStatementASTNode } as ReturnFunctionStatementASTNode
        return returnStatement.expression.fold(
            ifLeft = { Type.UnitType },
            ifRight = { typeScope.get(it)!! }
        )
    }
}