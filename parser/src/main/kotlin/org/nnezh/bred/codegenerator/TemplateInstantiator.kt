package org.nnezh.bred.codegenerator

import arrow.core.Either
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.ArrayDeclarationASTNode
import org.nnezh.bred.ast.ArrayElementAccessASTNode
import org.nnezh.bred.ast.ArrayInitializationExpressionASTNode
import org.nnezh.bred.ast.AssignmentStatementAstNode
import org.nnezh.bred.ast.BinaryExpressionASTNode
import org.nnezh.bred.ast.BlockAstNode
import org.nnezh.bred.ast.BooleanLiteralExpressionASTNode
import org.nnezh.bred.ast.CallFunctionStatementAstNode
import org.nnezh.bred.ast.DeclareGlobalVariableASTNode
import org.nnezh.bred.ast.DoubleLiteralExpressionASTNode
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.bred.ast.FieldAccessChainExpressionASTNode
import org.nnezh.bred.ast.ForStatementAstNode
import org.nnezh.bred.ast.FunctionArgument
import org.nnezh.bred.ast.FunctionCallExpressionASTNode
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.GenericParam
import org.nnezh.bred.ast.IfStatementAstNode
import org.nnezh.bred.ast.IntLiteralExpressionASTNode
import org.nnezh.bred.ast.LongLiteralExpressionASTNode
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.ast.ReturnFunctionStatementAstNode
import org.nnezh.bred.ast.ScalarVariableInitializationASTNode
import org.nnezh.bred.ast.StatementAstNode
import org.nnezh.bred.ast.StringLiteralExpressionASTNode
import org.nnezh.bred.ast.TypeClassDeclAstNode
import org.nnezh.bred.ast.UnaryExpressionASTNode
import org.nnezh.bred.ast.VariableExpressionASTNode
import org.nnezh.bred.ast.WhileStatementAstNode
import org.nnezh.bred.ast.cloneSubtree
import org.nnezh.bred.common.TypeNames
import org.nnezh.bred.common.TypeRef
import org.nnezh.bred.common.TypeSign
import org.nnezh.bred.common.toTypeRef
import org.nnezh.bred.context.BuiltInFunctionMeta
import org.nnezh.bred.context.DeclaredFunctionMeta
import org.nnezh.bred.context.InstanceMeta
import org.nnezh.bred.context.ProgramGlobalContext
import org.nnezh.bred.context.mangleFunctionName

class TemplateInstantiator(
    private val context: ProgramGlobalContext,
) {
    private lateinit var templates: Map<String, FunctionDeclAstNode>
    private lateinit var typeClassMethods: Map<String, List<TypeClassMethodOwner>>

    private val generatedTemplates: MutableMap<TemplateInstanceKey, FunctionDeclAstNode> = mutableMapOf()
    private val templatesInProgress: MutableSet<TemplateInstanceKey> = mutableSetOf()
    private val signaturesByName: MutableMap<String, FunctionSignature> = mutableMapOf()

    fun instantiate(root: ProgramRoot): Either<ASTError, ProgramRoot> =
        try {
            Either.Right(instantiateOrAbort(root))
        } catch (abort: InstantiationAbort) {
            Either.Left(abort.error)
        }

    private fun instantiateOrAbort(root: ProgramRoot): ProgramRoot {
        templates = root.functions
            .filter { it.genericParams.isNotEmpty() }
            .associateBy { it.name }
        typeClassMethods = root.typeClasses
            .flatMap { typeClass -> typeClass.methods.map { method -> TypeClassMethodOwner(typeClass, method.name) } }
            .groupBy { it.methodName }

        val instanceFunctions = root.instances.flatMap { instance ->
            instance.methods.map { method ->
                cloneSubtree(method).copy(name = mangleFunctionName(method.name, method.arguments, method.result))
            }
        }

        val concreteFunctions = root.functions
            .filter { it.genericParams.isEmpty() }
            .map { function -> transformConcreteFunction(cloneSubtree(function)) }

        val allFunctions = mutableListOf<FunctionDeclAstNode>()
        (concreteFunctions + instanceFunctions).forEach { addFunctionOrAbort(allFunctions, it) }
        generatedTemplates.values.forEach { addFunctionOrAbort(allFunctions, it) }

        return ProgramRoot(
            functions = allFunctions,
            globalVariables = root.globalVariables.map { cloneGlobalVariable(it) },
            types = root.types.map { cloneSubtree(it) },
            typeClasses = emptyList(),
            instances = emptyList(),
        )
    }

    private fun transformConcreteFunction(function: FunctionDeclAstNode): FunctionDeclAstNode {
        val scope = Scope()
        function.arguments.forEach { scope.bind(it.name, it.scopeType()) }

        return function.copy(
            body = transformBlock(function.body, scope, TemplateRewriteContext.empty()),
        )
    }

    private fun generateTemplateFunction(
        template: FunctionDeclAstNode,
        argumentTypes: List<TypeSign>,
    ): FunctionDeclAstNode {
        val mapping = inferGenericMapping(template, argumentTypes)
        val replacedArguments = template.arguments.map { it.replaceTypes(mapping) }
        val replacedResult = replaceType(template.result, mapping)
        val key = TemplateInstanceKey(template.name, replacedArguments.map { it.scopeType() }, replacedResult)
        generatedTemplates[key]?.let { return it }

        val generatedName = mangleFunctionName(template.name, replacedArguments, replacedResult)
        val reservedSignature = FunctionSignature(replacedArguments.map { it.scopeType() }, replacedResult)
        ensureNameAvailable(generatedName, reservedSignature)
        if (key in templatesInProgress) {
            return template.copy(
                name = generatedName,
                genericParams = emptyList(),
                arguments = replacedArguments,
                result = replacedResult,
            )
        }

        templatesInProgress.add(key)
        val scope = Scope()
        replacedArguments.forEach { scope.bind(it.name, it.scopeType()) }
        val rewriteContext = TemplateRewriteContext.from(template.genericParams, mapping)
        val body = transformBlock(template.body, scope, rewriteContext)
        templatesInProgress.remove(key)

        val generated = FunctionDeclAstNode(
            name = generatedName,
            genericParams = emptyList(),
            arguments = replacedArguments,
            result = replacedResult,
            body = body,
        )
        generatedTemplates[key] = generated
        return generated
    }

    private fun transformBlock(
        block: BlockAstNode,
        parentScope: Scope,
        rewriteContext: TemplateRewriteContext,
    ): BlockAstNode {
        val scope = Scope(parentScope)
        return BlockAstNode(block.statements.map { transformStatement(it, scope, rewriteContext) })
    }

    private fun transformStatement(
        statement: StatementAstNode,
        scope: Scope,
        rewriteContext: TemplateRewriteContext,
    ): StatementAstNode =
        when (statement) {
            is ScalarVariableInitializationASTNode -> {
                val expression = transformExpression(statement.expression, scope, rewriteContext)
                val transformed = statement.copy(
                    type = replaceType(statement.type, rewriteContext.typeMapping),
                    expression = expression,
                )
                scope.bind(transformed.name, transformed.type)
                transformed
            }
            is ArrayDeclarationASTNode -> {
                val transformedType = replaceType(statement.type, rewriteContext.typeMapping)
                val transformed = statement.copy(
                    type = transformedType,
                    expression = statement.expression?.let { transformExpression(it, scope, rewriteContext) },
                )
                scope.bind(transformed.name, transformed.arrayScopeType())
                transformed
            }
            is AssignmentStatementAstNode -> statement.copy(
                lValue = transformExpression(statement.lValue, scope, rewriteContext),
                rValue = transformExpression(statement.rValue, scope, rewriteContext),
            )
            is CallFunctionStatementAstNode -> statement.copy(
                expression = transformExpression(statement.expression, scope, rewriteContext),
            )
            is ReturnFunctionStatementAstNode -> statement.copy(
                expression = statement.expression?.let { transformExpression(it, scope, rewriteContext) },
            )
            is IfStatementAstNode -> statement.copy(
                condition = transformExpression(statement.condition, scope, rewriteContext),
                thenBlock = transformBlock(statement.thenBlock, scope, rewriteContext),
                elseBlock = statement.elseBlock?.let { transformBlock(it, scope, rewriteContext) },
            )
            is WhileStatementAstNode -> statement.copy(
                condition = transformExpression(statement.condition, scope, rewriteContext),
                bodyBlock = transformBlock(statement.bodyBlock, scope, rewriteContext),
            )
            is ForStatementAstNode -> statement.copy(
                desugaredContent = transformBlock(statement.desugaredContent, scope, rewriteContext),
            )
        }

    private fun transformExpression(
        expression: ExpressionASTNode,
        scope: Scope,
        rewriteContext: TemplateRewriteContext,
    ): ExpressionASTNode =
        when (expression) {
            is IntLiteralExpressionASTNode -> expression
            is LongLiteralExpressionASTNode -> expression
            is DoubleLiteralExpressionASTNode -> expression
            is BooleanLiteralExpressionASTNode -> expression
            is StringLiteralExpressionASTNode -> expression
            is VariableExpressionASTNode -> cloneSubtree(expression)
            is ArrayInitializationExpressionASTNode -> expression.copy(
                args = expression.args.map { transformExpression(it, scope, rewriteContext) },
            )
            is ArrayElementAccessASTNode -> expression.copy(
                index = transformExpression(expression.index, scope, rewriteContext),
            )
            is BinaryExpressionASTNode -> expression.copy(
                left = transformExpression(expression.left, scope, rewriteContext),
                right = transformExpression(expression.right, scope, rewriteContext),
            )
            is UnaryExpressionASTNode -> expression.copy(
                operand = transformExpression(expression.operand, scope, rewriteContext),
            )
            is FunctionCallExpressionASTNode -> transformCallExpression(expression, scope, rewriteContext)
            is FieldAccessChainExpressionASTNode -> expression
        }

    private fun transformCallExpression(
        expression: FunctionCallExpressionASTNode,
        scope: Scope,
        rewriteContext: TemplateRewriteContext,
    ): FunctionCallExpressionASTNode {
        val transformedArguments = expression.arguments.map { transformExpression(it, scope, rewriteContext) }

        templates[expression.name]?.let { template ->
            val argumentTypes = expression.arguments.map { inferType(it, scope, rewriteContext) }
            val generated = generateTemplateFunction(template, argumentTypes)
            return FunctionCallExpressionASTNode(generated.name, transformedArguments)
        }

        if (expression.name in typeClassMethods) {
            val argumentTypes = expression.arguments.map { inferType(it, scope, rewriteContext) }
            var receiverType = argumentTypes.firstOrNull()
            if (receiverType?.isArray == true) {
                receiverType = receiverType.elementTypeOrNull
            }
            resolveTypeClassMethod(expression.name, receiverType, rewriteContext)?.let { method ->
                return FunctionCallExpressionASTNode(
                    name = mangleFunctionName(method.name, method.arguments, method.result),
                    arguments = transformedArguments,
                )
            }
        }

        return FunctionCallExpressionASTNode(expression.name, transformedArguments)
    }

    private fun resolveTypeClassMethod(
        methodName: String,
        receiverType: TypeSign?,
        rewriteContext: TemplateRewriteContext,
    ): FunctionDeclAstNode? {
        receiverType ?: return null
        val owners = typeClassMethods[methodName].orEmpty()
        if (owners.isEmpty()) {
            return null
        }

        val allowedTypeClasses = rewriteContext.constraintsByConcreteType[receiverType.toManglePart()].orEmpty()
        val candidates = owners.filter { owner ->
            allowedTypeClasses.isEmpty() || owner.typeClass.name in allowedTypeClasses
        }
        if (candidates.isEmpty()) {
            return null
        }
        val owner = candidates.singleOrNull()
            ?: fail("Ambiguous typeclass method $methodName for type ${receiverType.name}")
        val instance: InstanceMeta = if (receiverType.isArray) {
            context.findInstance(owner.typeClass.name, receiverType.elementTypeOrNull ?: receiverType)
        } else {
            context.findInstance(owner.typeClass.name, receiverType)
        } ?: fail("Missing instance ${owner.typeClass.name}<${receiverType.name}> for method $methodName")
        return instance.methods[methodName]
            ?: fail("Instance ${owner.typeClass.name}<${receiverType.name}> does not implement $methodName")
    }

    private fun inferType(
        expression: ExpressionASTNode,
        scope: Scope,
        rewriteContext: TemplateRewriteContext,
    ): TypeSign =
        when (expression) {
            is IntLiteralExpressionASTNode -> context.intType
            is LongLiteralExpressionASTNode -> context.longType
            is DoubleLiteralExpressionASTNode -> context.doubleType
            is BooleanLiteralExpressionASTNode -> context.booleanType
            is StringLiteralExpressionASTNode -> context.stringType
            is VariableExpressionASTNode -> scope.lookup(expression.token.lexeme)
                ?: fail("Cannot infer type for variable ${expression.token.lexeme}")
            is ArrayElementAccessASTNode -> scope.lookup(expression.name)
                ?.arrayElementType()
                ?: fail("Cannot infer type for array ${expression.name}")
            is UnaryExpressionASTNode -> inferType(expression.operand, scope, rewriteContext)
            is BinaryExpressionASTNode -> inferType(expression.left, scope, rewriteContext)
            is ArrayInitializationExpressionASTNode -> expression.args.firstOrNull()
                ?.let { inferType(it, scope, rewriteContext) }
                ?: fail("Cannot infer type for empty array initializer")
            is FunctionCallExpressionASTNode -> {
                val argumentTypes = expression.arguments.map { inferType(it, scope, rewriteContext) }
                templates[expression.name]?.let { template ->
                    val mapping = inferGenericMapping(template, argumentTypes)
                    return replaceType(template.result, mapping)
                }
                resolveTypeClassMethod(expression.name, argumentTypes.firstOrNull(), rewriteContext)?.let { return it.result }
                when (val meta = context.functions[expression.name]) {
                    is DeclaredFunctionMeta -> meta.astNode.result
                    is BuiltInFunctionMeta -> context.externFunctionSignatures
                        .firstOrNull { it.name == meta.name && it.args == argumentTypes }
                        ?.resultType
                        ?: context.unitType
                    null -> context.constructors[expression.name]?.let { context.typeRegistry.findType(expression.name) }
                        ?: fail("Cannot infer return type for function ${expression.name}")
                //
                }
            }

            is FieldAccessChainExpressionASTNode -> TODO()
        }

    private fun inferGenericMapping(
        template: FunctionDeclAstNode,
        argumentTypes: List<TypeSign>,
    ): Map<String, TypeSign> {
        if (argumentTypes.size != template.arguments.size) {
            fail("Cannot instantiate ${template.name}: expected ${template.arguments.size} arguments but got ${argumentTypes.size}")
        }

        val genericNames = template.genericParams.map { it.name }.toSet()
        val mapping = mutableMapOf<String, TypeSign>()
        template.arguments.zip(argumentTypes).forEach { (parameter, argumentType) ->
            val parameterType = parameter.scopeType()
            inferGenericMappingFromParameter(parameter.name, parameterType, argumentType, genericNames, mapping)
        }

        template.genericParams.forEach { generic ->
            if (generic.name !in mapping) {
                fail("Cannot infer concrete type for generic parameter ${generic.name} in ${template.name}")
            }
        }

        return mapping
    }

    private fun inferGenericMappingFromParameter(
        parameterName: String,
        parameterType: TypeSign,
        argumentType: TypeSign,
        genericNames: Set<String>,
        mapping: MutableMap<String, TypeSign>,
    ) {
        if (parameterType.arguments.isEmpty() && parameterType.name in genericNames) {
            bindGeneric(parameterType.name, argumentType, mapping)
            return
        }
        if (!containsGeneric(parameterType, genericNames)) {
            return
        }
        if (!sameGenericContainer(parameterType, argumentType) ||
            parameterType.arguments.size != argumentType.arguments.size
        ) {
            fail("Cannot infer generic type from complex parameter $parameterName: ${parameterType.render()}")
        }

        parameterType.arguments.zip(argumentType.arguments).forEach { (parameterArgument, argumentArgument) ->
            inferGenericMappingFromParameter(parameterName, parameterArgument, argumentArgument, genericNames, mapping)
        }
    }

    private fun bindGeneric(
        genericName: String,
        argumentType: TypeSign,
        mapping: MutableMap<String, TypeSign>,
    ) {
        val normalizedType = context.resolve(argumentType) ?: argumentType
        val previous = mapping.putIfAbsent(genericName, normalizedType)
        if (previous != null && previous != normalizedType) {
            fail("Conflicting concrete types for $genericName: ${previous.render()} and ${normalizedType.render()}")
        }
    }

    private fun sameGenericContainer(parameterType: TypeSign, argumentType: TypeSign): Boolean =
        if (parameterType.isArrayLike() || argumentType.isArrayLike()) {
            parameterType.isArrayLike() && argumentType.isArrayLike()
        } else {
            parameterType.name == argumentType.name
        }

    private fun TypeSign.isArrayLike(): Boolean =
        isArray || name == TypeNames.ARRAY

    private fun replaceType(type: TypeSign, mapping: Map<String, TypeSign>): TypeSign =
        mapping[type.name]?.takeIf { type.arguments.isEmpty() }
            ?: if (type.isArray) {
                context.arrayOf(replaceType(type.elementTypeOrNull ?: fail("Array type has no element type"), mapping))
            } else if (type.arguments.isEmpty()) {
                type
            } else {
                val args = type.arguments.map { replaceType(it, mapping).toTypeRef() }
                context.resolveOrThrow(TypeRef(type.name, args))
            }

    private fun FunctionArgument.replaceTypes(mapping: Map<String, TypeSign>): FunctionArgument =
        copy(type = replaceType(type, mapping))

    private fun FunctionArgument.scopeType(): TypeSign =
        if (isArray) context.arrayOf(type) else type

    private fun ArrayDeclarationASTNode.arrayScopeType(): TypeSign =
        if (type.isArray) type else context.arrayOf(type)

    private fun TypeSign.arrayElementType(): TypeSign? =
        if (isArray) elementTypeOrNull else null

    private fun containsGeneric(type: TypeSign, genericNames: Set<String>): Boolean =
        type.name in genericNames || type.arguments.any { containsGeneric(it, genericNames) }

    private fun cloneGlobalVariable(node: DeclareGlobalVariableASTNode): DeclareGlobalVariableASTNode =
        node.copy(expression = node.expression?.let { cloneSubtree(it) })

    private fun addFunctionOrAbort(
        functions: MutableList<FunctionDeclAstNode>,
        function: FunctionDeclAstNode,
    ) {
        val signature = FunctionSignature(function.arguments.map { it.scopeType() }, function.result)
        ensureNameAvailable(function.name, signature)
        signaturesByName[function.name] = signature
        functions.add(function)
    }

    private fun ensureNameAvailable(name: String, signature: FunctionSignature) {
        val existing = signaturesByName[name] ?: return
        if (existing != signature) {
            fail("Generated function name collision for $name")
        }
    }

    private fun fail(message: String): Nothing =
        throw InstantiationAbort(ASTError(message))

    private data class Scope(
        private val parent: Scope? = null,
        private val bindings: MutableMap<String, TypeSign> = mutableMapOf(),
    ) {
        fun bind(name: String, type: TypeSign) {
            bindings[name] = type
        }

        fun lookup(name: String): TypeSign? =
            bindings[name] ?: parent?.lookup(name)
    }

    private data class TemplateRewriteContext(
        val typeMapping: Map<String, TypeSign>,
        val constraintsByConcreteType: Map<String, Set<String>>,
    ) {
        companion object {
            fun empty(): TemplateRewriteContext = TemplateRewriteContext(emptyMap(), emptyMap())

            fun from(
                genericParams: List<GenericParam>,
                mapping: Map<String, TypeSign>,
            ): TemplateRewriteContext =
                TemplateRewriteContext(
                    typeMapping = mapping,
                    constraintsByConcreteType = genericParams.mapNotNull { generic ->
                        val concreteType = mapping[generic.name] ?: return@mapNotNull null
                        concreteType.toManglePart() to generic.constraints.toSet()
                    }.toMap(),
                )
        }
    }

    private data class TypeClassMethodOwner(
        val typeClass: TypeClassDeclAstNode,
        val methodName: String,
    )

    private data class TemplateInstanceKey(
        val name: String,
        val argumentTypes: List<TypeSign>,
        val result: TypeSign,
    )

    private data class FunctionSignature(
        val argumentTypes: List<TypeSign>,
        val result: TypeSign,
    )

    private class InstantiationAbort(
        val error: ASTError,
    ) : RuntimeException(error.message)
}
