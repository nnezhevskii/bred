package org.nnezh.org.nnezh.semantic.analyzers

import arrow.core.left
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
import org.nnezh.org.nnezh.semantic.generic.BuiltInMethods
import org.nnezh.org.nnezh.semantic.generic.FunctionSignature
import org.nnezh.org.nnezh.semantic.generic.SemanticError
import org.nnezh.org.nnezh.semantic.generic.SemanticErrorType
import org.nnezh.org.nnezh.semantic.generic.SemanticSubAnalyzer
import java.util.Collections.singletonList

class FunctionSubAnalyzer: SemanticSubAnalyzer() {
    public lateinit var registry: FunctionRegistry

    override fun analyzeProgramASTNode(node: ProgramASTNode): List<SemanticError> {
        registry = FunctionRegistry()
        BuiltInMethods.functions.forEach { registry.registerFunction(it) }

        node.functions.map { FunctionSignature.build(it) }.forEach {
            if (registry.isFunctionRegistered(it.name, it.args)) {
                return singletonList(
                    SemanticError.FunctionSemanticError(
                        where = node,
                        critical = true,
                        SemanticErrorType.REDEFINE_FUNCTION
                    )
                )
            }
            registry.registerFunction(it)
        }

        return node.globalVariables.flatMap { varNode -> analyzeVariableInitializationASTNode(varNode) } +
                node.functions.flatMap { functionASTNode -> functionASTNode.args.arguments.flatMap { argNode -> analyzeFunctionArgumentASTNode(argNode) } +
                    analyzeBlockASTNode(functionASTNode.block)
        }
    }

    override fun analyzeBlockASTNode(node: BlockASTNode): List<SemanticError> {
        return node.statements.flatMap { astNode -> routeStatementHandling(astNode) }
    }

    override fun analyzeDeclareFunctionASTNode(node: DeclareFunctionASTNode): List<SemanticError> {
        return emptyList()
    }

    override fun analyzeEmptyNode(node: EmptyNode): List<SemanticError> {
        return emptyList()
    }

    override fun analyzeBinaryExpressionASTNode(node: BinaryExpressionASTNode): List<SemanticError> {
        return routeExpressionHandling(node.left) + routeExpressionHandling(node.right)
    }

    override fun analyzeFunctionCallExpressionNode(node: FunctionCallExpressionNode): List<SemanticError> {
        val result = mutableListOf<SemanticError>()
        if (!registry.existFunctionWithName(node.name.lexeme)) {
            result.add(SemanticError.FunctionSemanticError(
                where = node,
                critical = true,
                errorType = SemanticErrorType.FUNCTION_NOT_FOUND
            ))
            // тут точно правильно
        } else if (!registry.isFunctionWithSameArityRegistered(node.name.lexeme, node.arguments.size)) {
            result.add(SemanticError.FunctionSemanticError(
                where = node,
                critical = true,
                errorType = SemanticErrorType.FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS_AMOUNT
            ))
        }
        result.addAll(node.arguments.flatMap { arg -> routeExpressionHandling(arg) })

        return result
    }

    override fun analyzeBooleanLiteralExpressionNode(node: BooleanLiteralExpressionNode): List<SemanticError> {
        return emptyList()
    }

    override fun analyzeDoubleLiteralExpressionNode(node: DoubleLiteralExpressionNode): List<SemanticError> {
        return emptyList()
    }

    override fun analyzeIntLiteralExpressionNode(node: IntLiteralExpressionNode): List<SemanticError> {
        return emptyList()
    }

    override fun analyzeStringLiteralExpressionNode(node: StringLiteralExpressionNode): List<SemanticError> {
        return emptyList()
    }

    override fun analyzeUnaryExpressionASTNode(node: UnaryExpressionASTNode): List<SemanticError> {
        return routeExpressionHandling(node.operand)
    }

    override fun analyzeVariableExpressionNode(node: VariableExpressionNode): List<SemanticError> {

        return emptyList()
    }

    override fun analyzeFunctionArgumentASTNode(node: FunctionArgumentASTNode): List<SemanticError> {
        return emptyList()
    }

    override fun analyzeAssignmentStatementASTNode(node: AssignmentStatementASTNode): List<SemanticError> {
        val result = mutableListOf<SemanticError>()

        result.addAll(routeExpressionHandling(node.lValue))
        result.addAll(routeExpressionHandling(node.rValue))

        return result
    }

    override fun analyzeCallFunctionStatementASTNode(node: CallFunctionStatementASTNode): List<SemanticError> {
        return routeExpressionHandling(node.expression)
    }

    override fun analyzeForStatementASTNode(node: ForStatementASTNode): List<SemanticError> {
        return analyzeBlockASTNode(node.desugaredContent)
    }

    override fun analyzeIfStatementASTNode(node: IfStatementASTNode): List<SemanticError> {
        val result = mutableListOf<SemanticError>()
        result.addAll(routeExpressionHandling(node.condition))
        result.addAll(analyzeBlockASTNode(node.thenBlock))
        node.elseBlock.fold(
            ifLeft = { result.addAll(analyzeBlockASTNode(it)) },
            ifRight = {}
        )

        return result
    }

    override fun analyzeReturnFunctionStatementASTNode(node: ReturnFunctionStatementASTNode): List<SemanticError> {
        val result = mutableListOf<SemanticError>()

        node.expression.fold(
            ifLeft = {},
            ifRight = { result.addAll(routeExpressionHandling(it)) }
        )

        return result
    }

    override fun analyzeVariableInitializationASTNode(node: VariableInitializationASTNode): List<SemanticError> {
        val result = mutableListOf<SemanticError>()
//        if ( functionRegistry.existFunctionWithName(node.variableName)) {
//            result.add(SemanticError.FunctionSemanticError(
//                where = node,
//                critical = true,
//                errorType = SemanticErrorType.FUNCTION_IS_USED_AS_VARIABLE
//            ))
//        }
        result.addAll(routeExpressionHandling(node.valExpression))

        return result
    }

    override fun analyzeWhileStatementASTNode(node: WhileStatementASTNode): List<SemanticError> {
        val result = mutableListOf<SemanticError>()
        result.addAll(routeExpressionHandling(node.condition))
        result.addAll(analyzeBlockASTNode(node.bodyBlock))

        return result
    }
}

class FunctionRegistry {
    private val functionsRegistry = mutableMapOf<String, MutableList<FunctionSignature>>()

    fun existFunctionWithName(name: String): Boolean {
        return functionsRegistry.containsKey(name)
    }

    fun isFunctionWithSameArityRegistered(function: String, argsSize: Int): Boolean {
        return functionsRegistry[function]?.any { signature ->
            signature.name == function && signature.args.size == argsSize
        } ?: false
    }

    fun isFunctionRegistered(function: String, args: List<Type>): Boolean {
        return functionsRegistry[function]?.any { signature ->
            signature.name == function && signature.args == args
        } ?: false
    }

    fun registerFunction(function: FunctionSignature) {
        functionsRegistry.computeIfAbsent(function.name) { mutableListOf() }.add(function)
    }

    fun getResultType(function: String, args: List<Type>): Type? {
        return functionsRegistry
            .get(function)
            ?.firstOrNull { signature -> signature.args.size == args.size && signature.args.all { type -> type in args }  }
            ?.returnType
    }
}