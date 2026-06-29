package org.nnezh

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.context.bind
import arrow.core.raise.either
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
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.ast.ReturnFunctionStatementAstNode
import org.nnezh.bred.ast.ScalarVariableInitializationASTNode
import org.nnezh.bred.ast.StatementAstNode
import org.nnezh.bred.ast.StringLiteralExpressionASTNode
import org.nnezh.bred.ast.TypeClassDeclAstNode
import org.nnezh.bred.ast.TypeClassMethodDeclAstNode
import org.nnezh.bred.ast.TypeSign
import org.nnezh.bred.ast.UnaryExpressionASTNode
import org.nnezh.bred.ast.VariableExpressionASTNode
import org.nnezh.bred.ast.WhileStatementAstNode
import org.nnezh.bred.context.DeclaredFunctionMeta
import org.nnezh.bred.context.ProgramGlobalContext
import java.util.Collections.singletonList
import java.util.IdentityHashMap
import kotlin.collections.flatMap
import kotlin.collections.set

class SemanticAnalyzer(val globalContext: ProgramGlobalContext) {
    private val typeValidator = TypeValidator(globalContext)

    fun analyze(root: ProgramRoot): Either<List<SemanticError>, List<SemanticError.SemanticWarning>> {
        val scope = Scope()
        root.functions.forEach { function ->
            // TODO: Type Checking
            scope.registerFunction(
                FunctionSignature(
                    function.name,
                    function.arguments.map { it.type },
                    function.result
                )
            )
        }
        scope.registerFunction(
            FunctionSignature("println", listOf(getPrimitiveType("String")!!), TypeSign("Unit"))
        )

        // TODO << BuiltIn Functions
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
                val newScope = scope.innerScope()
                node.arguments.forEach { arg ->
                    newScope.put(arg.name, arg.type)
                }
                // TODO: check function redeclaration (same name + same arguments).
                return visit(node.body, newScope)
            }

            is BlockAstNode -> {
                val innerScope = scope.innerScope()
                val warnings = mutableListOf<SemanticError.SemanticWarning>()
                node.statements.forEach { node ->
                    visit(node, innerScope).fold(
                        ifLeft = { return it.left() },
                        ifRight = { warnings.addAll(it) }
                    )
                }
                return warnings.right()
            }

            is DeclareGlobalVariableASTNode -> TODO()
            is DeclareTypeASTNode -> TODO()
            EmptyNode -> {
                return listOf<SemanticError.SemanticWarning>().right()
            }

            is ArrayElementAccessASTNode -> {
                scope.findVariable(node.name)?.first ?: return singletonList(
                    SemanticError.VariableScopeSemanticError(
                        node,
                        SemanticErrorType.UNKNOWN_VARIABLE
                    )
                ).left()
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
                            warnings.right()
                        }

                    }
                )
            }

            is ArrayInitializationExpressionASTNode -> TODO()
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
            is AssignmentStatementAstNode -> TODO()
            is CallFunctionStatementAstNode -> {
                return visit(node.expression, scope)

            }

            is ArrayDeclarationASTNode -> TODO()
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

                scope.put(node.name, node.type)
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
                    visit(node.thenBlock, scope).fold(
                        ifLeft = { return it.left() },
                        ifRight = { }
                    )
                    node.elseBlock?.let { it ->
                        visit(it, scope).fold(
                            ifLeft = { return it.left() },
                            ifRight = { }
                        )
                    }
                    return emptyList<SemanticError.SemanticWarning>().right()

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
                // TODO << check returns value.
                return emptyList<SemanticError.SemanticWarning>().right()
            }

            is TypeClassDeclAstNode -> {
                TODO() /* Should not be there */
            }

            is TypeClassMethodDeclAstNode -> {
                TODO() /* Should not be there */
            }
        }
    }

    data class FunctionSignature(
        val name: String,
        val args: List<TypeSign>,
        val resultType: TypeSign
    )

    private data class Scope(
        val parentScope: Scope? = null,
        private val variablesType: MutableMap<String, TypeSign> = mutableMapOf(),
        private val expressionTypeTable: IdentityHashMap<ExpressionASTNode, TypeSign> = IdentityHashMap(),
        private val registeredFunctions: MutableList<FunctionSignature> = mutableListOf()
    ) {

        fun innerScope() = Scope(this, mutableMapOf(), expressionTypeTable, mutableListOf())

        fun put(variable: String, type: TypeSign) {
            variablesType[variable] = type
        }

        fun put(expression: ExpressionASTNode, type: TypeSign) {
            expressionTypeTable[expression] = type
        }

        fun registerFunction(function: FunctionSignature) {
            registeredFunctions.add(function)
        }

        fun functionExist(name: String): Boolean { // any function with this name exist. Signature may differ
            return registeredFunctions.any { it.name == name }
        }

        fun getFunction(name: String, args: List<TypeSign>): FunctionSignature? {
            return registeredFunctions.find {
                it.name == name &&
                        it.args == args
            } ?: parentScope?.getFunction(name, args)
        }

        private fun get(variable: String): TypeSign? = variablesType[variable] ?: parentScope?.get(variable)
        fun get(expression: ExpressionASTNode): TypeSign? = expressionTypeTable[expression]


        fun findVariable(variable: String): Pair<TypeSign, Int>? {
            if (variablesType[variable] != null) {
                return Pair(variablesType[variable]!!, 0)
            }
            if (parentScope == null) {
                return null
            }
            val pair = parentScope.findVariable(variable) ?: return null
            return Pair(pair.first, pair.second + 1)
        }
    }

    private fun getPrimitiveType(type: String): TypeSign? {
        return globalContext.types[type]?.let { TypeSign(it.name) }
    }
}