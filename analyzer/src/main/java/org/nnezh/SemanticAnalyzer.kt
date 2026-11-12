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
import org.nnezh.bred.ast.FieldAccessChainExpressionASTNode
import org.nnezh.bred.ast.ForStatementAstNode
import org.nnezh.bred.ast.FunctionCallExpressionASTNode
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.IfStatementAstNode
import org.nnezh.bred.ast.InstanceDeclAstNode
import org.nnezh.bred.ast.IntLiteralExpressionASTNode
import org.nnezh.bred.ast.LeftValue
import org.nnezh.bred.ast.LongLiteralExpressionASTNode
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.ast.ReturnFunctionStatementAstNode
import org.nnezh.bred.ast.ScalarVariableInitializationASTNode
import org.nnezh.bred.ast.StringLiteralExpressionASTNode
import org.nnezh.bred.ast.SumTypeVariantASTNode
import org.nnezh.bred.ast.SumTypeVariantFieldASTNode
import org.nnezh.bred.ast.TypeClassDeclAstNode
import org.nnezh.bred.ast.TypeClassMethodDeclAstNode
import org.nnezh.bred.ast.UnaryExpressionASTNode
import org.nnezh.bred.ast.VariableExpressionASTNode
import org.nnezh.bred.ast.WhenBranchAstNode
import org.nnezh.bred.ast.WhenExpressionASTNode
import org.nnezh.bred.ast.WhenStatementAstNode
import org.nnezh.bred.ast.WhileStatementAstNode
import org.nnezh.bred.common.FunctionSignature
import org.nnezh.bred.common.SymbolId
import org.nnezh.bred.common.SymbolIdGenerator
import org.nnezh.bred.common.TypeRef
import org.nnezh.bred.common.TypeSign
import org.nnezh.bred.common.toTypeRef
import org.nnezh.bred.context.DeclaredFunctionMeta
import org.nnezh.bred.context.ProgramGlobalContext
import org.nnezh.bred.context.SumTypeDefinition
import org.nnezh.bred.context.SumVariantDefinition
import java.util.Collections.singletonList
import java.util.IdentityHashMap
import kotlin.collections.flatMap
import kotlin.collections.listOf
import kotlin.collections.set

data class SemanticAnalysisResult(
    val warnings: List<SemanticError.SemanticWarning>,
    val expressionTypes: Map<ExpressionASTNode, TypeSign>,
    val resolvedSymbols: Map<ASTNode, SymbolId>,
)

class SemanticAnalyzer(val globalContext: ProgramGlobalContext) {
    private val typeValidator = TypeValidator(globalContext)
    private val idGenerator: SymbolIdGenerator = SymbolIdGenerator()

    fun analyze(root: ProgramRoot): Either<List<SemanticError>, List<SemanticError.SemanticWarning>> {
        return analyzeWithResult(root).fold(
            ifLeft = { it.left() },
            ifRight = { it.warnings.right() },
        )
    }

    fun analyzeWithResult(root: ProgramRoot): Either<List<SemanticError>, SemanticAnalysisResult> {
        val scope = Scope()

        globalContext.externFunctionSignatures.forEach { scope.registerFunction(idGenerator.nextFunction(it.name, isExtern = true), it) }

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
            val args = function.arguments.map { if (it.isArray) globalContext.arrayOf(it.type) else it.type }
            if (scope.getFunction(name, args) != null) {
                return singletonList(
                    SemanticError.VariableScopeSemanticError(
                        function,
                        SemanticErrorType.REDEFINE_FUNCTION,
                        SemanticDiagnosticDetail.ControlFlow("function '${function.name}' with arguments ${args.renderArguments()} is already declared.")
                    )
                ).left()
            }
            val fnId = idGenerator.nextFunction(name, isExtern = false)
            scope.bindSymbol(function, fnId) // связываем само объявление функции с её ID
            scope.registerFunction(
                id = fnId,
                function = FunctionSignature(name, args, function.result)
            )
        }

        return visit(root, scope).fold(
            ifLeft = { it.left() },
            ifRight = {
                SemanticAnalysisResult(
                    warnings = it,
                    expressionTypes = scope.expressionTypesSnapshot(),
                    resolvedSymbols = scope.resolvedSymbolsSnapshot(),
                ).right()
            },
        )
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
                val paramWarnings = mutableListOf<SemanticError.SemanticWarning>()
                node.arguments.forEach { arg ->
                    val argType = if (arg.isArray) globalContext.arrayOf(arg.type) else arg.type
                    declareVariable(
                        node = node,
                        name = arg.name,
                        id = idGenerator.nextParam(arg.name),
                        type = argType,
                        isMutable = false,
                        scope = newScope, // <-- в newScope, не в scope!
                        warnings = paramWarnings,
                    ).fold(
                        ifLeft = { return it.left() },
                        ifRight = {}
                    )
                }
                visit(node.body, newScope).fold(
                    ifLeft = { return it.left() },
                    ifRight = { warnings ->
                        if (node.result != unitType() && !blockGuaranteesReturn(node.body)) {
                            return singletonList(
                                SemanticError.ControlFlowSemanticError(
                                    node.body,
                                    true,
                                    SemanticErrorType.EXPLICIT_RETURN_IS_EXPECTED,
                                    SemanticDiagnosticDetail.ControlFlow("function '${node.name}' must return ${node.result.render()} on all paths.")
                                )
                            ).left()
                        }
                        return (paramWarnings + warnings).right()
                    }
                )
            }

            is BlockAstNode -> {
                val innerScope = scope.innerScope()
                val warnings = mutableListOf<SemanticError.SemanticWarning>()
                if (scope.checkControlFlow) {
                    val flow = analyzeBlockFlow(node)
                    if (flow.errors.isNotEmpty()) {
                        return flow.errors.left()
                    }
                    warnings.addAll(flow.warnings)
                }
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
                declareVariable(
                    node = node,
                    name = node.name,
                    id = idGenerator.nextLocal(node.name), // или nextGlobal для глобальной
                    type = node.type,                      // или node.scopeType() для глобальной
                    isMutable = node.isMutable,
                    scope = scope,
                    warnings = warnings,
                ).fold(
                    ifLeft = { return it.left() },
                    ifRight = {}
                )

                val declaredType = requireNotNull(node.type) { "global variable type is required" }
                val arraySize = node.size

                node.expression?.let { initializer ->
                    scope.beginInitializing(node.name)
                    try {
                        visit(initializer, scope).fold(
                            ifLeft = { return it.left() },
                            ifRight = { warnings.addAll(it) }
                        )
                    } finally {
                        scope.endInitializing(node.name)
                    }

                    if (arraySize != null) {
                        val declaredElementType = declaredType.arrayElementType()
                        val arrayInitializer = initializer as? ArrayInitializationExpressionASTNode
                        if (arrayInitializer != null && arraySize != arrayInitializer.args.size) {
                            return singletonList(
                                SemanticError.TypeSemanticError(
                                    node,
                                    SemanticErrorType.INVALID_AMOUNT_OF_ARGUMENTS_IN_ARRAYS_INITIALIZATION,
                                    SemanticDiagnosticDetail.ArraySizeMismatch(node.name, arraySize, arrayInitializer.args.size)
                                )
                            ).left()
                        }

                        val initializerElementType = scope.get(initializer)
                        if (initializerElementType != null && initializerElementType != declaredElementType) {
                            return singletonList(
                                SemanticError.TypeSemanticError(
                                    node,
                                    SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
                                    SemanticDiagnosticDetail.TypeMismatch(
                                        "initialize global array '${node.name}: ${declaredElementType.render()}[$arraySize]'",
                                        declaredElementType,
                                        initializerElementType,
                                    )
                                )
                            ).left()
                        }
                    } else {
                        val initializerType = scope.get(initializer)
                        if (declaredType != initializerType) {
                            return singletonList(
                                SemanticError.TypeSemanticError(
                                    node,
                                    SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
                                    SemanticDiagnosticDetail.TypeMismatch(
                                        "initialize global '${node.name}: ${declaredType.render()}'",
                                        declaredType,
                                        initializerType,
                                    )
                                )
                            ).left()
                        }
                    }

                }

                declareVariable(
                    node = node,
                    scope = scope,
                    name = node.name,
                    id = idGenerator.nextGlobal(node.name),
                    type = node.scopeType(),
                    isMutable = node.isMutable,
                    warnings = warnings,
                )

                return warnings.right()
            }

            is DeclareTypeASTNode,
            is SumTypeVariantASTNode,
            is SumTypeVariantFieldASTNode,
            is WhenBranchAstNode -> {
                return emptyList<SemanticError.SemanticWarning>().right()
            }
            EmptyNode -> {
                return listOf<SemanticError.SemanticWarning>().right()
            }

            is ArrayElementAccessASTNode -> {
                if (scope.isInitializing(node.name)) {
                    return singletonList(
                        SemanticError.VariableScopeSemanticError(
                            node,
                            SemanticErrorType.UNKNOWN_VARIABLE,
                            SemanticDiagnosticDetail.UnknownVariable(node.name)
                        )
                    ).left()
                }

                val variable = scope.findVariable(node.name)?.first ?: return singletonList(
                    SemanticError.VariableScopeSemanticError(
                        node,
                        SemanticErrorType.UNKNOWN_VARIABLE,
                        SemanticDiagnosticDetail.UnknownVariable(node.name)
                    )
                ).left()

                if (!variable.type.isArray) {
                    return singletonList(
                        SemanticError.VariableScopeSemanticError(
                            node,
                            SemanticErrorType.ARRAY_IS_EXPECTED_BUT_GOT_SCALAR,
                            SemanticDiagnosticDetail.ArrayExpected(node.name, variable.type)
                        )
                    ).left()
                }

                val warnings = mutableListOf<SemanticError.SemanticWarning>()
                visit(node.index, scope).fold(
                    ifLeft = { return it.left() },
                    ifRight = {
                        warnings.addAll(it)
                        return if (scope.get(node.index) != globalContext.intType) {
                            singletonList(
                                SemanticError.VariableScopeSemanticError(
                                    node,
                                    SemanticErrorType.ARRAY_INDEX_IS_NOT_INTEGER,
                                    SemanticDiagnosticDetail.ArrayIndexType(node.name, scope.get(node.index))
                                )
                            ).left()
                        } else {
                            scope.put(node, scope.findVariable(node.name)!!.first.type.elementTypeOrNull!!)
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
                            SemanticErrorType.TYPE_CHECKER_INCONSISTENT_ARRAY_TYPE,
                            SemanticDiagnosticDetail.ControlFlow(
                                "array initializer contains mixed element types: ${types.joinToString(", ") { it.render() }}."
                            )
                        )
                    ).left()
                }
                types.firstOrNull()?.let { scope.put(node, it) }
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
                            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
                            SemanticDiagnosticDetail.TypeMismatch(
                                "apply operator '${node.operator.lexeme}'",
                                null,
                                TypeRef("Operands", listOf(leftType.toTypeRef(), rightType.toTypeRef())),
                            )
                        )
                    ).left()
                scope.put(node, type)
                return mutableListOf<SemanticError.SemanticWarning>().right()
            }

            is FunctionCallExpressionASTNode -> {
                return handleFunctionCallExpression(node, scope)
            }

            is BooleanLiteralExpressionASTNode -> {
                scope.put(node, globalContext.booleanType)
                return listOf<SemanticError.SemanticWarning>().right()
            }

            is DoubleLiteralExpressionASTNode -> {
                scope.put(node, globalContext.doubleType)
                return listOf<SemanticError.SemanticWarning>().right()
            }

            is IntLiteralExpressionASTNode -> {
                scope.put(node, globalContext.intType)
                return listOf<SemanticError.SemanticWarning>().right()
            }

            is LongLiteralExpressionASTNode -> {
                scope.put(node, globalContext.longType)
                return listOf<SemanticError.SemanticWarning>().right()
            }

            is StringLiteralExpressionASTNode -> {
                scope.put(node, globalContext.stringType)
                return listOf<SemanticError.SemanticWarning>().right()
            }

            is UnaryExpressionASTNode -> {
                visit(node.operand, scope).fold(
                    ifLeft = { return it.left() },
                    ifRight = { }
                )
                val operandType = scope.get(node.operand)!!
                val unaryType = typeValidator.produceUnaryType(node.operator.kind, operandType)
                if (unaryType == null) {
                    return singletonList(
                        SemanticError.TypeSemanticError(
                            node,
                            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
                            SemanticDiagnosticDetail.TypeMismatch(
                                "apply unary operator '${node.operator.lexeme}'",
                                null,
                                operandType,
                            )
                        )
                    ).left()
                }
                scope.put(node, unaryType)
                return emptyList<SemanticError.SemanticWarning>().right()
            }

            is VariableExpressionASTNode -> {
                if (scope.isInitializing(node.token.lexeme)) {
                    return singletonList(
                        SemanticError.VariableScopeSemanticError(
                            node,
                            SemanticErrorType.UNKNOWN_VARIABLE,
                            SemanticDiagnosticDetail.UnknownVariable(node.token.lexeme)
                        )
                    ).left()
                }

                val type = scope.findVariable(node.token.lexeme)?.first
                if (type == null) {
                    val constructors = globalContext.constructorsByName(node.token.lexeme)
                        .filter { it.arguments.isEmpty() }
                    if (constructors.size == 1) {
                        scope.put(node, constructors.single().resultType)
                        return emptyList<SemanticError.SemanticWarning>().right()
                    }

                    return singletonList(
                        SemanticError.VariableScopeSemanticError(
                            node,
                            SemanticErrorType.UNKNOWN_VARIABLE,
                            SemanticDiagnosticDetail.UnknownVariable(node.token.lexeme)
                        )
                    ).left()
                } else {
                    scope.put(node, type.type)
                    return emptyList<SemanticError.SemanticWarning>().right()
                }
            }

            is InstanceDeclAstNode -> TODO()
            is AssignmentStatementAstNode -> {
                if (node.lValue !is LeftValue) {
                    return singletonList(
                        SemanticError.TypeSemanticError(
                            node,
                            SemanticErrorType.UNEXPECTED_LVALUE,
                            SemanticDiagnosticDetail.ControlFlow("assignment target '${node.lValue.renderExpression()}' is not assignable.")
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
                                SemanticErrorType.VARIABLE_CHANGING_IMMUTABLE,
                                SemanticDiagnosticDetail.ImmutableAssignment((node.lValue as VariableExpressionASTNode).token.lexeme)
                            )
                        ).left()
                    }
//                    VARIABLE_CHANGING_IMMUTABLE
                }
                if (node.lValue is FieldAccessChainExpressionASTNode) {
                    val target = fieldAccessTarget(node.lValue as FieldAccessChainExpressionASTNode, scope)
                        ?: return buildError(node.lValue, SemanticErrorType.UNKNOWN_FIELD).left()
                    if (!globalContext.isMutableProduct(target.ownerType)) {
                        return singletonList(
                            SemanticError.TypeSemanticError(
                                node,
                                SemanticErrorType.VARIABLE_CHANGING_IMMUTABLE,
                                SemanticDiagnosticDetail.ImmutableAssignment(node.lValue.renderExpression())
                            )
                        ).left()
                    }
                }

                if (lValueType != rValueType) {
                    return singletonList(
                        SemanticError.TypeSemanticError(
                            node,
                            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
                            SemanticDiagnosticDetail.TypeMismatch(
                                "assign to '${node.lValue.renderExpression()}'",
                                lValueType,
                                rValueType,
                            )
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
                node.expression?.let { initializer ->
                    scope.beginInitializing(node.name)
                    try {
                        visit(initializer, scope).fold(
                            ifLeft = { error -> return error.left() },
                            ifRight = { initializerWarnings ->
                                val expectedSize = node.size
                                val actualSize = (node.expression as ArrayInitializationExpressionASTNode).args.size

                                if (expectedSize != actualSize) {
                                    return singletonList(
                                        SemanticError.TypeSemanticError(
                                            node,
                                            SemanticErrorType.INVALID_AMOUNT_OF_ARGUMENTS_IN_ARRAYS_INITIALIZATION,
                                            SemanticDiagnosticDetail.ArraySizeMismatch(node.name, expectedSize, actualSize)
                                        )
                                    ).left()
                                }

                                val initializerElementType = scope.get(initializer)
                                if (initializerElementType != null && initializerElementType != node.type.arrayElementType()) {
                                    return singletonList(
                                        SemanticError.TypeSemanticError(
                                            node,
                                            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
                                            SemanticDiagnosticDetail.TypeMismatch(
                                                "initialize array '${node.name}: ${node.type.arrayElementType().render()}[$expectedSize]'",
                                                node.type.arrayElementType(),
                                                initializerElementType,
                                            )
                                        )
                                    ).left()
                                }


                                warnings.addAll(initializerWarnings)
                            }
                        )
                    } finally {
                        scope.endInitializing(node.name)
                    }
                }
                declareVariable(
                    node = node,
                    scope = scope,
                    name = node.name,
                    id = idGenerator.nextLocal(node.name),
                    type = node.type.asArrayType(),
                    isMutable = node.isMutable,
                    warnings = warnings,
                )
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
                                SemanticErrorType.VARIABLE_REDECLARATION,
                                SemanticDiagnosticDetail.VariableRedeclaration(node.name)
                            )
                        ).left()
                    }
                    warnings.add(SemanticError.SemanticWarning(node, SemanticErrorType.VARIABLE_OVERSHADOW))
                }

                scope.beginInitializing(node.name)
                try {
                    visit(node.expression, scope).fold(
                        ifLeft = { return it.left() },
                        ifRight = { warnings.addAll(it) }
                    )
                } finally {
                    scope.endInitializing(node.name)
                }

                val initializerType = scope.get(node.expression)
                if (node.type != initializerType) {
                    return singletonList(
                        SemanticError.TypeSemanticError(
                            node,
                            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
                            SemanticDiagnosticDetail.TypeMismatch(
                                "initialize '${node.name}: ${node.type.render()}'",
                                node.type,
                                initializerType,
                            )
                        )
                    ).left()
                }
                declareVariable(
                    node = node,
                    scope = scope,
                    name = node.name,
                    id = idGenerator.nextLocal(node.name),
                    type = node.type,
                    isMutable = node.isMutable,
                    warnings = warnings,
                )
                return warnings.right()
            }

            is ForStatementAstNode -> {
                return visit(node.desugaredContent, scope.withControlFlowChecks(false))
            }

            is IfStatementAstNode -> {
                val warnings = mutableListOf<SemanticError.SemanticWarning>()
                visit(node.condition, scope).fold(
                    ifLeft = { return it.left() },
                    ifRight = { warnings.addAll(it) }
                )
                if (scope.get(node.condition) != globalContext.booleanType) {
                    return singletonList(
                        SemanticError.TypeSemanticError(
                            node.condition,
                            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
                            SemanticDiagnosticDetail.TypeMismatch(
                                "use if condition",
                                globalContext.booleanType,
                                scope.get(node.condition),
                            )
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
                if (scope.get(node.condition) != globalContext.booleanType) {
                    return singletonList(
                        SemanticError.TypeSemanticError(
                            node.condition,
                            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
                            SemanticDiagnosticDetail.TypeMismatch(
                                "use while condition",
                                globalContext.booleanType,
                                scope.get(node.condition),
                            )
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
                            SemanticErrorType.METHOD_HAS_WRONG_RETURN,
                            SemanticDiagnosticDetail.WrongReturn(expectedReturnType, actualReturnType)
                        )
                    ).left()
                }

                return warnings.right()
            }

            is FieldAccessChainExpressionASTNode -> {
                val warnings = mutableListOf<SemanticError.SemanticWarning>()
                // TODO What about SomeMethod().counter. << ... Can be not only variables here but also methods

                val target = fieldAccessTarget(node, scope)
                    ?: return buildError(node, SemanticErrorType.UNKNOWN_FIELD).left()
                scope.put(node, target.fieldType)


                return warnings.right()
            }

            is WhenStatementAstNode -> {
                return visit(node.expression, scope)
            }

            is WhenExpressionASTNode -> {
                return visitWhenExpression(node, scope)
            }

            is TypeClassDeclAstNode -> { TODO() /* Should not be there */ }
            is TypeClassMethodDeclAstNode -> { TODO() /* Should not be there */ }
        }
    }

    private data class VariableBinding(
        val id: SymbolId,
        val type: TypeSign,
        val isMutable: Boolean
    )

    private data class FunctionBinding(
        val id: SymbolId.Function,
        val signature: FunctionSignature,
    )

    private data class Scope(
        val parentScope: Scope? = null,
        private val variables: MutableMap<String, VariableBinding> = mutableMapOf(),
        private val expressionTypeTable: IdentityHashMap<ExpressionASTNode, TypeSign> = IdentityHashMap(),
        private val resolvedSymbols: IdentityHashMap<ASTNode, SymbolId> = IdentityHashMap(),
        private val registeredFunctions: MutableList<FunctionBinding> = mutableListOf(),
        private val initializingVariables: MutableSet<String> = mutableSetOf(),

        val expectedReturnType: TypeSign? = null,
        val checkControlFlow: Boolean = true
    ) {

        fun putVariable(name: String, binding: VariableBinding) {
            variables[name] = binding
        }

        fun bindSymbol(node: ASTNode, symbolId: SymbolId) {
            resolvedSymbols[node] = symbolId
        }

        fun innerScope(expectedReturnType: TypeSign? = this.expectedReturnType) = Scope(
            parentScope = this,
            variables = mutableMapOf(),
            expressionTypeTable = expressionTypeTable, // общий на весь файл
            resolvedSymbols = resolvedSymbols,         // общий на весь файл!
            registeredFunctions = mutableListOf(),
            initializingVariables = mutableSetOf(),
            expectedReturnType = expectedReturnType,
            checkControlFlow = checkControlFlow,
        )

        fun withControlFlowChecks(enabled: Boolean) = copy(checkControlFlow = enabled)

        fun beginInitializing(variable: String) {
            initializingVariables.add(variable)
        }

        fun endInitializing(variable: String) {
            initializingVariables.remove(variable)
        }

        fun isInitializing(variable: String): Boolean =
            variable in initializingVariables || parentScope?.isInitializing(variable) == true

        fun put(expression: ExpressionASTNode, type: TypeSign) {
            expressionTypeTable[expression] = type
        }

        fun registerFunction(id: SymbolId.Function, function: FunctionSignature) {
            registeredFunctions.add(FunctionBinding(id, function))
        }

        fun expressionTypesSnapshot(): Map<ExpressionASTNode, TypeSign> =
            IdentityHashMap(expressionTypeTable)

        fun functionExist(name: String): Boolean { // any function with this name exist. Signature may differ
            if (registeredFunctions.any { it.signature.name == name }) {
                return true
            }
            if (parentScope == null) {
                return false
            }
            return parentScope.functionExist(name)
        }

        fun functionCandidates(name: String): List<FunctionSignature> =
            (registeredFunctions.filter { it.signature.name == name }).map { it.signature } + (parentScope?.functionCandidates(name) ?: emptyList())


        fun getFunction(name: String, args: List<TypeSign>): FunctionSignature? {
            return (registeredFunctions.find { binding ->
                binding.signature.name == name &&
                        binding.signature.args == args
            }?.signature ?: parentScope?.getFunction(name, args))
        }

        fun get(expression: ExpressionASTNode): TypeSign? = expressionTypeTable[expression]

        fun findVariable(variable: String): Pair<VariableBinding, Int>? {
            val local = variables[variable]
            if (local != null) {
                return Pair(local, 0)
            }
            val parentPair = parentScope?.findVariable(variable) ?: return null
            return Pair(parentPair.first, parentPair.second + 1)
        }

        fun isMutable(variable: String): Boolean {
            return variables[variable]?.isMutable ?: parentScope?.isMutable(variable) ?: false
        }
    }

    private fun DeclareGlobalVariableASTNode.scopeType(): TypeSign {
        val declaredType = requireNotNull(type) { "global variable type is required" }
        return if (size == null) declaredType else declaredType.asArrayType()
    }

    private fun TypeSign.asArrayType(): TypeSign =
        if (isArray) this else globalContext.arrayOf(this)

    private fun TypeSign.arrayElementType(): TypeSign =
        if (isArray) elementTypeOrNull!! else this

    private fun productFields(type: TypeSign): Map<String, TypeSign>? =
        globalContext.productFields(type)

    private data class FieldAccessTarget(
        val ownerType: TypeSign,
        val fieldType: TypeSign,
    )

    private data class SumBranchPattern(
        val variant: SumVariantDefinition,
        val bindings: List<VariableExpressionASTNode>,
    )

    private fun fieldAccessTarget(node: FieldAccessChainExpressionASTNode, scope: Scope): FieldAccessTarget? {
        var ownerType: TypeSign = scope.findVariable(node.root)?.first?.type ?: return null
        var current = node.next ?: return null
        while (true) {
            val fields = productFields(ownerType) ?: return null
            val fieldType = fields[current.root] ?: return null
            if (current.next == null) {
                return FieldAccessTarget(ownerType, fieldType)
            }
            ownerType = fieldType
            current = current.next!!
        }
    }

    private fun visitWhenExpression(
        node: WhenExpressionASTNode,
        scope: Scope,
    ): Either<List<SemanticError>, List<SemanticError.SemanticWarning>> {
        val warnings = mutableListOf<SemanticError.SemanticWarning>()
        visit(node.subject, scope).fold(
            ifLeft = { return it.left() },
            ifRight = { warnings.addAll(it) }
        )

        val subjectType = scope.get(node.subject)!!
        val sumDefinition = globalContext.sumDefinition(subjectType)
        val seenVariants = mutableSetOf<String>()
        var hasElse = false

        node.branches.forEach { branch ->
            val condition = branch.condition
            val branchScope = scope.innerScope()
            if (condition == null) {
                hasElse = true
            } else if (sumDefinition != null) {
                when (val patternResult = sumBranchPattern(condition, sumDefinition, scope)) {
                    is SumBranchPatternResult.Error -> return singletonList(patternResult.error).left()
                    SumBranchPatternResult.NotPattern -> {
                        visit(condition, scope).fold(
                            ifLeft = { return it.left() },
                            ifRight = { warnings.addAll(it) }
                        )
                        val conditionType = scope.get(condition)
                        if (conditionType != subjectType) {
                            return singletonList(
                                SemanticError.TypeSemanticError(
                                    condition,
                                    SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
                                    SemanticDiagnosticDetail.TypeMismatch(
                                        "match when branch against ${subjectType.render()}",
                                        subjectType,
                                        conditionType,
                                    )
                                )
                            ).left()
                        }
                    }
                    is SumBranchPatternResult.Pattern -> {
                        val pattern = patternResult.pattern
                        if (!seenVariants.add(pattern.variant.name)) {
                            return buildError(condition, SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES).left()
                        }
                        scope.put(condition, subjectType)
                        pattern.bindings.zip(pattern.variant.fields).forEach { (binding, field) ->
                            declareVariable(
                                node = node,
                                scope = scope,
                                name = binding.token.lexeme,
                                id = idGenerator.nextLocal(binding.token.lexeme),
                                type = field.type,
                                isMutable = false,
                                warnings = warnings,
                            )
                            branchScope.put(binding, field.type)
                        }
                    }
                }
            } else {
                visit(condition, scope).fold(
                    ifLeft = { return it.left() },
                    ifRight = { warnings.addAll(it) }
                )
                val conditionType = scope.get(condition)
                if (conditionType != subjectType) {
                    return singletonList(
                        SemanticError.TypeSemanticError(
                            condition,
                            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
                            SemanticDiagnosticDetail.TypeMismatch(
                                "match when branch against ${subjectType.render()}",
                                subjectType,
                                conditionType,
                            )
                        )
                    ).left()
                }
            }

            visit(branch.bodyBlock, branchScope).fold(
                ifLeft = { return it.left() },
                ifRight = { warnings.addAll(it) }
            )
        }

        if (sumDefinition != null && !hasElse) {
            val expectedVariants = sumDefinition.variants.map { it.name }.toSet()
            if (seenVariants != expectedVariants) {
                return singletonList(
                    SemanticError.TypeSemanticError(
                        node,
                        SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
                        SemanticDiagnosticDetail.ControlFlow(
                            "when over ${subjectType.render()} must cover variants ${expectedVariants.sorted().joinToString(", ")}."
                        )
                    )
                ).left()
            }
        }

        scope.put(node, unitType())
        return warnings.right()
    }

    private sealed interface SumBranchPatternResult {
        data class Pattern(val pattern: SumBranchPattern) : SumBranchPatternResult
        object NotPattern : SumBranchPatternResult
        data class Error(val error: SemanticError) : SumBranchPatternResult
    }

    private fun sumBranchPattern(
        condition: ExpressionASTNode,
        sumDefinition: SumTypeDefinition,
        scope: Scope,
    ): SumBranchPatternResult {
        val variant = when (condition) {
            is VariableExpressionASTNode -> sumDefinition.variants.singleOrNull { it.name == condition.token.lexeme }
                ?: return SumBranchPatternResult.Error(sumPatternError(condition, "unknown sum variant '${condition.token.lexeme}'."))
            is FunctionCallExpressionASTNode -> sumDefinition.variants.singleOrNull { it.name == condition.name }
                ?: return SumBranchPatternResult.NotPattern
            else -> return SumBranchPatternResult.NotPattern
        }

        if (condition is VariableExpressionASTNode) {
            return SumBranchPatternResult.Pattern(SumBranchPattern(variant, emptyList()))
        }

        condition as FunctionCallExpressionASTNode
        if (condition.arguments.size != variant.fields.size) {
            return SumBranchPatternResult.Error(
                sumPatternError(
                    condition,
                    "variant '${variant.name}' expects ${variant.fields.size} payload binding(s), got ${condition.arguments.size}.",
                )
            )
        }

        val bindings = mutableListOf<VariableExpressionASTNode>()
        val seenNames = mutableSetOf<String>()
        condition.arguments.forEach { argument ->
            val binding = argument as? VariableExpressionASTNode
                ?: return SumBranchPatternResult.Error(sumPatternError(argument, "sum payload binding must be a variable name."))
            val bindingName = binding.token.lexeme
            if (!seenNames.add(bindingName) || scope.findVariable(bindingName) != null) {
                return SumBranchPatternResult.Error(
                    SemanticError.TypeSemanticError(
                        binding,
                        SemanticErrorType.VARIABLE_REDECLARATION,
                        SemanticDiagnosticDetail.VariableRedeclaration(bindingName),
                    )
                )
            }
            bindings.add(binding)
        }

        return SumBranchPatternResult.Pattern(SumBranchPattern(variant, bindings))
    }

    private fun sumPatternError(node: ASTNode, message: String): SemanticError =
        SemanticError.TypeSemanticError(
            node,
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES,
            SemanticDiagnosticDetail.ControlFlow(message),
        )

    private fun unitType(): TypeSign = globalContext.unitType

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

                is ForStatementAstNode -> {}

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
                        SemanticErrorType.BLOCK_CONTAINS_MORE_THAN_ONE_RETURN,
                        SemanticDiagnosticDetail.ControlFlow("block contains a guaranteed return followed by another explicit return.")
                    )
                )
            } else {
                errors.add(
                    SemanticError.ControlFlowSemanticError(
                        block,
                        true,
                        SemanticErrorType.BLOCK_CONTAINS_CODE_AFTER_RETURN,
                        SemanticDiagnosticDetail.ControlFlow("block contains code after a guaranteed return.")
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

    private fun handleFunctionCallExpression(node: FunctionCallExpressionASTNode, scope: Scope): Either<List<SemanticError>, List<SemanticError.SemanticWarning>> {
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
                SemanticError.FunctionSemanticError(
                    node,
                    SemanticErrorType.COULDNT_RESOLVE_ARGUMENT_TYPE,
                    SemanticDiagnosticDetail.ControlFlow("could not resolve all argument types for call '${node.name}'.")
                )
            ).left()
        }

        val actualArgs = args.filterNotNull()
        val signature = newScope.getFunction(node.name, actualArgs)

        if (signature != null) {
            node.isConstructorCalling = false
            newScope.put(node, signature.resultType)
            return emptyList<SemanticError.SemanticWarning>().right()
        }

        val constructorCandidates = globalContext.constructorsByName(node.name)
        if (constructorCandidates.isNotEmpty()) {
            val matchingConstructors = constructorCandidates.filter { it.arguments == actualArgs }
            val constructor = matchingConstructors.singleOrNull()
                ?: return buildError(node, SemanticErrorType.CONSTRUCTOR_IS_FOUND_BUT_WRONG_ARGUMENTS).left()
            node.isConstructorCalling = true
            newScope.put(node, constructor.resultType)
            return emptyList<SemanticError.SemanticWarning>().right()
        }

        return if (newScope.functionExist(node.name)) {
            singletonList(
                SemanticError.FunctionSemanticError(
                    node,
                    SemanticErrorType.FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS,
                    SemanticDiagnosticDetail.FunctionWrongArguments(
                        node.name,
                        actualArgs,
                        newScope.functionCandidates(node.name),
                    )
                )
            ).left()
        } else {
            singletonList(
                SemanticError.FunctionSemanticError(
                    node,
                    SemanticErrorType.FUNCTION_NOT_FOUND,
                    SemanticDiagnosticDetail.FunctionNotFound(node.name, actualArgs)
                )
            ).left()
        }
    }

    private fun buildError(node: ASTNode, code: SemanticErrorType): List<SemanticError> {
        return singletonList(
            SemanticError.FunctionSemanticError(
                node,
                code
            )
        )

    }

    private fun declareVariable(
        node: ASTNode,
        name: String,
        id: SymbolId,
        type: TypeSign,
        isMutable: Boolean,
        scope: Scope,
        warnings: MutableList<SemanticError.SemanticWarning>,
    ): Either<List<SemanticError>, Unit> {
        val existing = scope.findVariable(name)
        if (existing != null) {
            if (existing.second == 0) {
                return singletonList(
                    SemanticError.TypeSemanticError(
                        node,
                        SemanticErrorType.VARIABLE_REDECLARATION,
                        SemanticDiagnosticDetail.VariableRedeclaration(name)
                    )
                ).left()
            }
            warnings.add(SemanticError.SemanticWarning(node, SemanticErrorType.VARIABLE_OVERSHADOW))
        }
        scope.putVariable(name, VariableBinding(id, type, isMutable))
        scope.bindSymbol(node, id) // Запоминаем ID самого объявления для 3АК!
        return Unit.right()
    }

    private fun List<TypeSign>.renderArguments(): String =
        joinToString(prefix = "(", postfix = ")") { it.render() }

    private fun ExpressionASTNode.renderExpression(): String =
        when (this) {
            is BooleanLiteralExpressionASTNode -> value.toString()
            is DoubleLiteralExpressionASTNode -> value.toString()
            is IntLiteralExpressionASTNode -> value.toString()
            is LongLiteralExpressionASTNode -> "${value}L"
            is StringLiteralExpressionASTNode -> "\"${value.escape()}\""
            is VariableExpressionASTNode -> token.lexeme
            is ArrayElementAccessASTNode -> "$name[${index.renderExpression()}]"
            is ArrayInitializationExpressionASTNode -> args.joinToString(prefix = "[", postfix = "]") { it.renderExpression() }
            is BinaryExpressionASTNode -> "${left.renderExpression()} ${operator.lexeme} ${right.renderExpression()}"
            is FunctionCallExpressionASTNode -> "$name(${arguments.joinToString(", ") { it.renderExpression() }})"
            is UnaryExpressionASTNode -> "${operator.lexeme}${operand.renderExpression()}"
            is FieldAccessChainExpressionASTNode -> this.root + (this.next?.let { n -> "." + n.renderExpression() } ?: "")
            is WhenExpressionASTNode -> "when (${subject.renderExpression()})"
        }

    private fun String.escape(): String =
        replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\"", "\\\"")
}
