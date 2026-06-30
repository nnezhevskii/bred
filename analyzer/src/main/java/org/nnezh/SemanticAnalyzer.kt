package org.nnezh

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.nnezh.bred.ast.ASTNode
import org.nnezh.bred.ast.ArrayDeclarationASTNode
import org.nnezh.bred.ast.ArrayElementAccessASTNode
import org.nnezh.bred.ast.ArrayInitializationExpressionASTNode
import org.nnezh.bred.ast.AssignmentStatementAstNode
import org.nnezh.bred.ast.BinaryExpressionASTNode
import org.nnezh.bred.ast.BlockAstNode
import org.nnezh.bred.ast.BooleanLiteralExpressionASTNode
import org.nnezh.bred.ast.CallFunctionStatementAstNode
import org.nnezh.bred.ast.DeclareGlobalVariableASTNode
import org.nnezh.bred.ast.DeclareTypeASTNode
import org.nnezh.bred.ast.DoubleLiteralExpressionASTNode
import org.nnezh.bred.ast.EmptyNode
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.bred.ast.ForStatementAstNode
import org.nnezh.bred.ast.FunctionCallExpressionASTNode
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.IfStatementAstNode
import org.nnezh.bred.ast.InstanceDeclAstNode
import org.nnezh.bred.ast.IntLiteralExpressionASTNode
import org.nnezh.bred.ast.LeftValue
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.ast.ReturnFunctionStatementAstNode
import org.nnezh.bred.ast.ScalarVariableInitializationASTNode
import org.nnezh.bred.ast.StringLiteralExpressionASTNode
import org.nnezh.bred.ast.TypeClassDeclAstNode
import org.nnezh.bred.ast.TypeClassMethodDeclAstNode
import org.nnezh.bred.ast.UnaryExpressionASTNode
import org.nnezh.bred.ast.VariableExpressionASTNode
import org.nnezh.bred.ast.WhileStatementAstNode
import org.nnezh.bred.common.BuiltInMethods
import org.nnezh.bred.common.FunctionSignature
import org.nnezh.bred.common.TypeSign
import org.nnezh.bred.context.DeclaredFunctionMeta
import org.nnezh.bred.context.ProgramGlobalContext
import java.lang.classfile.Attributes.signature
import java.util.Collections.singletonList
import java.util.IdentityHashMap
import kotlin.collections.flatMap
import kotlin.collections.set

class SemanticAnalyzer(val globalContext: ProgramGlobalContext) {
    private val typeValidator = TypeValidator(globalContext)

    fun analyze(root: ProgramRoot): Either<List<SemanticError>, List<SemanticError.SemanticWarning>> {
        val scope = Scope()

        BuiltInMethods.functions.forEach { scope.registerFunction(it) }

        root.globalVariables.forEach {
            visit(it, scope).fold(
                ifLeft = { it ->
                    return it.left()
                },
                ifRight = {}
            )
        }

        root.functions.forEach { function ->
            val name = function.name
            val args = function.arguments.map { if (it.isArray) TypeSign("Array", listOf(it.type)) else it.type }
            if (scope.getFunction(name, args) != null) {
                return singletonList(
                    SemanticError.VariableScopeSemanticError(
                        function,
                        SemanticErrorType.REDEFINE_FUNCTION
                    )
                ).left()
            }
            scope.registerFunction(
                FunctionSignature(
                    name,
                    args,
                    function.result
                )
            )
        }

        return visit(root, scope)
    }

    private fun visit(node: ASTNode, scope: Scope): Either<List<SemanticError>, List<SemanticError.SemanticWarning>> {
        when (node) {
            is ProgramRoot -> {
                val criticalErrors = mutableListOf<SemanticError>()
                val warnings = mutableListOf<SemanticError.SemanticWarning>()
                node.functions.forEach { function ->
                    visit(function, scope).fold(
                        ifLeft = { criticalErrors.addAll(it) },
                        ifRight = { warnings.addAll(it) }
                    )
                }
                return if (criticalErrors.isNotEmpty()) {
                    criticalErrors.left()
                } else {
                    warnings.right()
                }
            }

            is FunctionDeclAstNode -> {
                val newScope = scope.innerScope(node.result)
                node.arguments.forEach { arg ->
                    val argType = if (arg.isArray) TypeSign("Array", listOf(arg.type)) else arg.type
                    newScope.put(arg.name, argType, false)
                }
                visit(node.body, newScope).fold(
                    ifLeft = { return it.left() },
                    ifRight = { warnings ->
                        if (node.result != unitType() && !blockGuaranteesReturn(node.body)) {
                            return singletonList(
                                SemanticError.ControlFlowSemanticError(
                                    node.body,
                                    true,
                                    SemanticErrorType.EXPLICIT_RETURN_IS_EXPECTED
                                )
                            ).left()
                        }
                        return warnings.right()
                    }
                )
            }

            is BlockAstNode -> {
                val innerScope = scope.innerScope()
                val warnings = mutableListOf<SemanticError.SemanticWarning>()
                val flow = analyzeBlockFlow(node)
                if (flow.errors.isNotEmpty()) {
                    return flow.errors.left()
                }
                warnings.addAll(flow.warnings)
                node.statements.forEach { node ->
                    visit(node, innerScope).fold(
                        ifLeft = { return it.left() },
                        ifRight = { warnings.addAll(it) }
                    )
                }
                return warnings.right()
            }

            is DeclareGlobalVariableASTNode -> {
                val warnings = mutableListOf<SemanticError.SemanticWarning>()
                val pair = scope.findVariable(node.name)
                if (pair != null) {
                    if (pair.second == 0) {
                        return singletonList(
                            SemanticError.TypeSemanticError(
                                node,
                                SemanticErrorType.VARIABLE_REDECLARATION
                            )
                        ).left()
                    }
                    warnings.add(SemanticError.SemanticWarning(node, SemanticErrorType.VARIABLE_OVERSHADOW))
                }

                scope.put(node.name, node.type!!, node.isMutable)
                node.expression?.let {
                    visit(it, scope).fold(
                        ifLeft = { return it.left() },
                        ifRight = { warnings.addAll(it) }
                    )

                    if (node.type != scope.get(it)) {
                        return singletonList(
                            SemanticError.TypeSemanticError(
                                node,
                                SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES
                            )
                        ).left()
                    }

                }

                return warnings.right()
            }

            is DeclareTypeASTNode -> TODO()
            EmptyNode -> {
                return listOf<SemanticError.SemanticWarning>().right()
            }

            is ArrayElementAccessASTNode -> {
                val variable = scope.findVariable(node.name)?.first ?: return singletonList(
                    SemanticError.VariableScopeSemanticError(
                        node,
                        SemanticErrorType.UNKNOWN_VARIABLE
                    )
                ).left()

                if (scope.findVariable(node.name)!!.first.name != "Array") {
                    return singletonList(
                        SemanticError.VariableScopeSemanticError(
                            node,
                            SemanticErrorType.ARRAY_IS_EXPECTED_BUT_GOT_SCALAR
                        )
                    ).left()
                }

                val warnings = mutableListOf<SemanticError.SemanticWarning>()
                visit(node.index, scope).fold(
                    ifLeft = { return it.left() },
                    ifRight = {
                        warnings.addAll(it)
                        return if (scope.get(node.index) != getPrimitiveType("Int")) {
                            singletonList(
                                SemanticError.VariableScopeSemanticError(
                                    node,
                                    SemanticErrorType.ARRAY_INDEX_IS_NOT_INTEGER
                                )
                            ).left()
                        } else {
                            scope.put(node, scope.findVariable(node.name)!!.first.args[0])
                            warnings.right()
                        }

                    }
                )
            }

            is ArrayInitializationExpressionASTNode -> {
                val warnings = mutableListOf<SemanticError.SemanticWarning>()
                node.args.forEach { arg ->
                    visit(arg, scope).fold(
                        ifLeft = { return it.left() },
                        ifRight = { warnings.addAll(it) }
                    )
                }
                val types = node.args.map { arg -> scope.get(arg)!! }.toSet()
                if (types.size > 1) {
                    return singletonList(
                        SemanticError.VariableScopeSemanticError(
                            node,
                            SemanticErrorType.TYPE_CHECKER_INCONSISTENT_ARRAY_TYPE
                        )
                    ).left()
                }
                scope.put(node, types.first())
                return warnings.right()
            }

            is BinaryExpressionASTNode -> {
                val warnings = mutableListOf<SemanticError.SemanticWarning>()
                visit(node.left, scope).fold(
                    ifLeft = { return it.left() },
                    ifRight = { warnings.addAll(it) }
                )

                visit(node.right, scope).fold(
                    ifLeft = { return it.left() },
                    ifRight = { warnings.addAll(it) }
                )

                val leftType = scope.get(node.left)!!
                val rightType = scope.get(node.right)!!

                val type =
                    typeValidator.produceBinaryType(node.operator.kind, leftType, rightType) ?: return singletonList(
                        SemanticError.TypeSemanticError(
                            node,
                            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES
                        )
                    ).left()
                scope.put(node, type)
                return mutableListOf<SemanticError.SemanticWarning>().right()
            }

            is FunctionCallExpressionASTNode -> {
                val warnings = mutableListOf<SemanticError.SemanticWarning>()
                node.arguments.forEach { argument ->
                    visit(argument, scope).fold(
                        ifLeft = { return it.left() },
                        ifRight = { warnings.addAll(it) }
                    )
                }
                val newScope = scope.innerScope()
                val args = node.arguments.map { newScope.get(it) }
                if (args.any { it == null }) {
                    return singletonList(
                        SemanticError.FunctionSemanticError(node, SemanticErrorType.COULDNT_RESOLVE_ARGUMENT_TYPE)
                    ).left()
                }

                val signature = newScope.getFunction(node.name, args.filterNotNull())
                if (signature == null) {
                    return if (newScope.functionExist(node.name)) {
                        singletonList(
                            SemanticError.FunctionSemanticError(
                                node,
                                SemanticErrorType.FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS
                            )
                        ).left()
                    } else {
                        singletonList(
                            SemanticError.FunctionSemanticError(node, SemanticErrorType.FUNCTION_NOT_FOUND)
                        ).left()
                    }
                } else {
                    newScope.put(node, signature.resultType)
                    return emptyList<SemanticError.SemanticWarning>().right()
                }
            }

            is BooleanLiteralExpressionASTNode -> {
                scope.put(node, TypeSign("Boolean"))
                return listOf<SemanticError.SemanticWarning>().right()
            }

            is DoubleLiteralExpressionASTNode -> {
                scope.put(node, TypeSign("Double"))
                return listOf<SemanticError.SemanticWarning>().right()
            }

            is IntLiteralExpressionASTNode -> {
                scope.put(node, TypeSign("Int"))
                return listOf<SemanticError.SemanticWarning>().right()
            }

            is StringLiteralExpressionASTNode -> {
                scope.put(node, TypeSign("String"))
                return listOf<SemanticError.SemanticWarning>().right()
            }

            is UnaryExpressionASTNode -> {
                visit(node.operand, scope).fold(
                    ifLeft = { return it.left() },
                    ifRight = { }
                )
                val operandType = scope.get(node.operand)!!
                if (typeValidator.produceUnaryType(node.operator.kind, operandType) == null) {
                    return singletonList(
                        SemanticError.TypeSemanticError(
                            node,
                            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES
                        )
                    ).left()
                }
                return emptyList<SemanticError.SemanticWarning>().right()
            }

            is VariableExpressionASTNode -> {
                val type = scope.findVariable(node.token.lexeme)?.first
                if (type == null) {
                    return singletonList(
                        SemanticError.VariableScopeSemanticError(
                            node,
                            SemanticErrorType.UNKNOWN_VARIABLE
                        )
                    ).left()
                } else {
                    scope.put(node, type)
                    return emptyList<SemanticError.SemanticWarning>().right()
                }
            }

            is InstanceDeclAstNode -> TODO()
            is AssignmentStatementAstNode -> {
                if (node.lValue !is LeftValue) {
                    return singletonList(
                        SemanticError.TypeSemanticError(
                            node,
                            SemanticErrorType.UNEXPECTED_LVALUE
                        )
                    ).left()
                }

                val warnings = mutableListOf<SemanticError.SemanticWarning>()

                visit(node.lValue, scope).fold(
                    ifLeft = { return it.left() },
                    ifRight = { warnings.addAll(it) }
                )

                visit(node.rValue, scope).fold(
                    ifLeft = { return it.left() },
                    ifRight = { warnings.addAll(it) }
                )

                val lValueType = scope.get(node.lValue)!!
                val rValueType = scope.get(node.rValue)!!

                if (node.lValue is VariableExpressionASTNode) {
                    if (!scope.isMutable((node.lValue as VariableExpressionASTNode).token.lexeme)) {
                        return singletonList(
                            SemanticError.TypeSemanticError(
                                node,
                                SemanticErrorType.VARIABLE_CHANGING_IMMUTABLE
                            )
                        ).left()
                    }
//                    VARIABLE_CHANGING_IMMUTABLE
                }

                if (lValueType != rValueType) {
                    return singletonList(
                        SemanticError.TypeSemanticError(
                            node,
                            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES
                        )
                    ).left()
                }
                return warnings.right()
            }

            is CallFunctionStatementAstNode -> {
                return visit(node.expression, scope)
            }

            is ArrayDeclarationASTNode -> {
                val warnings = mutableListOf<SemanticError.SemanticWarning>()
                node.expression?.let {
                    visit(it, scope).fold(
                        ifLeft = { error -> return error.left() },
                        ifRight = {
                            val expectedSize = node.size
                            val actualSize = (node.expression as ArrayInitializationExpressionASTNode).args.size

                            if (expectedSize != actualSize) {
                                return singletonList(
                                    SemanticError.TypeSemanticError(
                                        node,
                                        SemanticErrorType.INVALID_AMOUNT_OF_ARGUMENTS_IN_ARRAYS_INITIALIZATION
                                    )
                                ).left()
                            }

                            scope.put(node.name, TypeSign("Array", listOf(node.type)), node.isMutable)
                            warnings.addAll(it)
                        }
                    )
                }
                return warnings.right()
            }

            is ScalarVariableInitializationASTNode -> {
                val warnings = mutableListOf<SemanticError.SemanticWarning>()
                val pair = scope.findVariable(node.name)
                if (pair != null) {
                    if (pair.second == 0) {
                        return singletonList(
                            SemanticError.TypeSemanticError(
                                node,
                                SemanticErrorType.VARIABLE_REDECLARATION
                            )
                        ).left()
                    }
                    warnings.add(SemanticError.SemanticWarning(node, SemanticErrorType.VARIABLE_OVERSHADOW))
                }

                scope.put(node.name, node.type, node.isMutable)
                visit(node.expression, scope).fold(
                    ifLeft = { return it.left() },
                    ifRight = { warnings.addAll(it) }
                )

                if (node.type != scope.get(node.expression)) {
                    return singletonList(
                        SemanticError.TypeSemanticError(
                            node,
                            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES
                        )
                    ).left()
                }
                return warnings.right()
            }

            is ForStatementAstNode -> {
                return visit(node.desugaredContent, scope)
            }

            is IfStatementAstNode -> {
                val warnings = mutableListOf<SemanticError.SemanticWarning>()
                visit(node.condition, scope).fold(
                    ifLeft = { return it.left() },
                    ifRight = { warnings.addAll(it) }
                )
                if (scope.get(node.condition) != getPrimitiveType("Boolean")) {
                    return singletonList(
                        SemanticError.TypeSemanticError(
                            node.condition,
                            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES
                        )
                    ).left()
                } else {
                    visit(node.thenBlock, scope).fold(
                        ifLeft = { return it.left() },
                        ifRight = { warnings.addAll(it) }
                    )
                    node.elseBlock?.let { it ->
                        visit(it, scope).fold(
                            ifLeft = { return it.left() },
                            ifRight = { warnings.addAll(it) }
                        )
                    }
                    return warnings.right()

                }
            }

            is WhileStatementAstNode -> {
                visit(node.condition, scope).fold(
                    ifLeft = { return it.left() },
                    ifRight = { }
                )
                if (scope.get(node.condition) != getPrimitiveType("Boolean")) {
                    return singletonList(
                        SemanticError.TypeSemanticError(
                            node.condition,
                            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES
                        )
                    ).left()
                } else {
                    visit(node.bodyBlock, scope).fold(
                        ifLeft = { return it.left() },
                        ifRight = { return it.right() }
                    )
                }
            }

            is ReturnFunctionStatementAstNode -> {
                if (!node.explicit) {
                    return emptyList<SemanticError.SemanticWarning>().right()
                }

                val expectedReturnType = scope.expectedReturnType ?: return emptyList<SemanticError.SemanticWarning>().right()
                val actualReturnType: TypeSign
                val warnings = mutableListOf<SemanticError.SemanticWarning>()
                val expression = node.expression

                if (expression == null) {
                    actualReturnType = unitType()
                } else {
                    visit(expression, scope).fold(
                        ifLeft = { return it.left() },
                        ifRight = { warnings.addAll(it) }
                    )
                    actualReturnType = scope.get(expression)!!
                }

                if (actualReturnType != expectedReturnType) {
                    return singletonList(
                        SemanticError.TypeSemanticError(
                            node,
                            SemanticErrorType.METHOD_HAS_WRONG_RETURN
                        )
                    ).left()
                }

                return warnings.right()
            }

            is TypeClassDeclAstNode -> {
                TODO() /* Should not be there */
            }

            is TypeClassMethodDeclAstNode -> {
                TODO() /* Should not be there */
            }
        }
    }


    private data class Scope(
        val parentScope: Scope? = null,
        private val variablesType: MutableMap<String, Pair<TypeSign, Boolean>> = mutableMapOf(),
        private val expressionTypeTable: IdentityHashMap<ExpressionASTNode, TypeSign> = IdentityHashMap(),
        private val registeredFunctions: MutableList<FunctionSignature> = mutableListOf(),
        val expectedReturnType: TypeSign? = null
    ) {

        fun innerScope(expectedReturnType: TypeSign? = this.expectedReturnType) =
            Scope(this, mutableMapOf(), expressionTypeTable, mutableListOf(), expectedReturnType)

        fun put(variable: String, type: TypeSign, isMutable: Boolean) {
            variablesType[variable] = Pair(type, isMutable)
        }

        fun put(expression: ExpressionASTNode, type: TypeSign) {
            expressionTypeTable[expression] = type
        }

        fun registerFunction(function: FunctionSignature) {
            registeredFunctions.add(function)
        }

        fun functionExist(name: String): Boolean { // any function with this name exist. Signature may differ
            if (registeredFunctions.any { it.name == name }) {
                return true
            }
            if (parentScope == null) {
                return false
            }
            return parentScope.functionExist(name)
        }

        fun getFunction(name: String, args: List<TypeSign>): FunctionSignature? {
            return registeredFunctions.find {
                it.name == name &&
                        it.args == args
            } ?: parentScope?.getFunction(name, args)
        }

        private fun get(variable: String): TypeSign? = variablesType[variable]?.first ?: parentScope?.get(variable)
        fun get(expression: ExpressionASTNode): TypeSign? = expressionTypeTable[expression]


        fun findVariable(variable: String): Pair<TypeSign, Int>? {
            if (variablesType[variable] != null) {
                return Pair(variablesType[variable]!!.first, 0)
            }
            if (parentScope == null) {
                return null
            }
            val pair = parentScope.findVariable(variable) ?: return null
            return Pair(pair.first, pair.second + 1)
        }

        fun isMutable(variable: String): Boolean {
            return variablesType[variable]?.second ?: parentScope?.isMutable(variable) ?: false
        }
    }

    private fun getPrimitiveType(type: String): TypeSign? {
        return globalContext.types[type]?.let { TypeSign(it.name) }
    }

    private fun unitType(): TypeSign = getPrimitiveType("Unit") ?: TypeSign("Unit")

    private data class BlockFlow(
        val errors: List<SemanticError>,
        val warnings: List<SemanticError.SemanticWarning>
    )

    private fun analyzeBlockFlow(block: BlockAstNode): BlockFlow {
        val effectiveStatements = block.statements.filterNot { it is ReturnFunctionStatementAstNode && !it.explicit }
        val errors = mutableListOf<SemanticError>()
        val warnings = mutableListOf<SemanticError.SemanticWarning>()

        effectiveStatements.forEach { statement ->
            when (statement) {
                is IfStatementAstNode -> {
                    val thenFlow = analyzeBlockFlow(statement.thenBlock)
                    errors.addAll(thenFlow.errors)
                    warnings.addAll(thenFlow.warnings)

                    statement.elseBlock?.let {
                        val elseFlow = analyzeBlockFlow(it)
                        errors.addAll(elseFlow.errors)
                        warnings.addAll(elseFlow.warnings)
                    }
                }

                is WhileStatementAstNode -> {
                    val bodyFlow = analyzeBlockFlow(statement.bodyBlock)
                    errors.addAll(bodyFlow.errors)
                    warnings.addAll(bodyFlow.warnings)
                }

                is ForStatementAstNode -> {
                    val bodyFlow = analyzeBlockFlow(statement.desugaredContent)
                    errors.addAll(bodyFlow.errors)
                    warnings.addAll(bodyFlow.warnings)
                }

                else -> {}
            }
        }

        val returningStatementIndex = effectiveStatements.indexOfFirst { statementGuaranteesReturn(it) }
        if (returningStatementIndex >= 0 && returningStatementIndex < effectiveStatements.lastIndex) {
            val statementsAfterReturn = effectiveStatements.drop(returningStatementIndex + 1)
            if (statementsAfterReturn.any { statementContainsExplicitReturn(it) }) {
                errors.add(
                    SemanticError.ControlFlowSemanticError(
                        block,
                        true,
                        SemanticErrorType.BLOCK_CONTAINS_MORE_THAN_ONE_RETURN
                    )
                )
            } else {
                errors.add(
                    SemanticError.ControlFlowSemanticError(
                        block,
                        true,
                        SemanticErrorType.BLOCK_CONTAINS_CODE_AFTER_RETURN
                    )
                )
            }
        }

        return BlockFlow(errors, warnings)
    }

    private fun blockGuaranteesReturn(block: BlockAstNode): Boolean =
        block.statements
            .filterNot { it is ReturnFunctionStatementAstNode && !it.explicit }
            .any { statementGuaranteesReturn(it) }

    private fun statementGuaranteesReturn(statement: ASTNode): Boolean =
        when (statement) {
            is ReturnFunctionStatementAstNode -> statement.explicit
            is IfStatementAstNode -> {
                val elseBlock = statement.elseBlock
                elseBlock != null &&
                        blockGuaranteesReturn(statement.thenBlock) &&
                        blockGuaranteesReturn(elseBlock)
            }
            else -> false
        }

    private fun statementContainsExplicitReturn(statement: ASTNode): Boolean =
        when (statement) {
            is ReturnFunctionStatementAstNode -> statement.explicit
            is IfStatementAstNode -> blockContainsExplicitReturn(statement.thenBlock) ||
                    statement.elseBlock?.let { blockContainsExplicitReturn(it) } == true
            is WhileStatementAstNode -> blockContainsExplicitReturn(statement.bodyBlock)
            is ForStatementAstNode -> blockContainsExplicitReturn(statement.desugaredContent)
            else -> false
        }

    private fun blockContainsExplicitReturn(block: BlockAstNode): Boolean =
        block.statements.any { statementContainsExplicitReturn(it) }
}
