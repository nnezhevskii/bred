package org.nnezh.org.nnezh.ICGenerator

import org.nnezh.bred.ast.ASTNode
import org.nnezh.bred.ast.ArrayDeclarationASTNode
import org.nnezh.bred.ast.ArrayElementAccessASTNode
import org.nnezh.bred.ast.ArrayInitializationExpressionASTNode
import org.nnezh.bred.ast.AssignmentStatementAstNode
import org.nnezh.bred.ast.BlockAstNode
import org.nnezh.bred.ast.BooleanLiteralExpressionASTNode
import org.nnezh.bred.ast.CallFunctionStatementAstNode
import org.nnezh.bred.ast.DeclareGlobalVariableASTNode
import org.nnezh.bred.ast.DoubleLiteralExpressionASTNode
import org.nnezh.bred.ast.EmptyNode
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.bred.ast.ForStatementAstNode
import org.nnezh.bred.ast.FunctionArgument
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.IfStatementAstNode
import org.nnezh.bred.ast.IntLiteralExpressionASTNode
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.ast.ReturnFunctionStatementAstNode
import org.nnezh.bred.ast.ScalarVariableInitializationASTNode
import org.nnezh.bred.ast.StringLiteralExpressionASTNode
import org.nnezh.bred.ast.VariableExpressionASTNode
import org.nnezh.bred.ast.WhileStatementAstNode
import org.nnezh.bred.common.TypeSign
import org.nnezh.bred.context.ProgramGlobalContext

class LLTACGenerator(
    private val nameEmitter: TACNameEmitter = TACNameEmitter(),
    private val typeTable: Map<ExpressionASTNode, TypeSign>,
    private val globalContext: ProgramGlobalContext,
) {
    private val mangledFunctions: MutableMap<String, String> = hashMapOf()
    private val expressionLLTACGenerator = LLTACExpressionSubgenerator(
        nameEmitter = nameEmitter,
        typeTable = typeTable,
        globalContext = globalContext,
        mangledFunctionsCallback = { mangledFunctions },
    )

    fun build(root: ASTNode): List<LLTACElement> =
        visit(root)

    fun visit(node: ASTNode): List<LLTACElement> {
        val instructions = mutableListOf<LLTACElement>()
        when (node) {
            is ProgramRoot -> {
                node.globalVariables.forEach { instructions.addAll(visit(it)) }
                node.functions.forEach { instructions.addAll(visit(it)) }
            }

            is FunctionDeclAstNode -> {
                val functionName = mangleFunctionName(node)
                mangledFunctions[node.name] = functionName
                instructions.add(LLTACElement.function(functionName, node.result))
                instructions.addAll(node.arguments.map { LLTACElement.getParam(it.name, it.scopeType()) })
                instructions.addAll(visit(node.body))
            }

            is BlockAstNode -> {
                node.statements.forEach { instructions.addAll(visit(it)) }
            }

            is AssignmentStatementAstNode -> {
                when (val lValue = node.lValue) {
                    is ArrayElementAccessASTNode -> {
                        val indexName = nameEmitter.nextVar()
                        val index = expressionLLTACGenerator.buildInstructionsForExpression(
                            variable = indexName,
                            type = globalContext.intType,
                            expression = lValue.index,
                        )
                        instructions.addAll(index.instructions)
                        if (indexName != index.finalVariable) {
                            instructions.add(LLTACElement.assignVariable(indexName, globalContext.intType, index.finalVariable!!))
                        }

                        val rightValueName = nameEmitter.nextVar()
                        val rightType = node.rValue.tacType()
                        val rightValue = expressionLLTACGenerator.buildInstructionsForExpression(
                            variable = rightValueName,
                            type = rightType,
                            expression = node.rValue,
                        )
                        instructions.addAll(rightValue.instructions)
                        instructions.add(
                            LLTACElement.store(
                                array = lValue.name,
                                index = indexName,
                                source = rightValue.finalVariable!!,
                                arrayType = globalContext.arrayOf(rightType),
                                elementType = rightType,
                                intType = globalContext.intType,
                            )
                        )
                    }

                    is VariableExpressionASTNode -> {
                        val varName = lValue.token.lexeme
                        val varType = lValue.tacType()
                        val result = expressionLLTACGenerator.buildInstructionsForExpression(varName, varType, node.rValue)
                        instructions.addAll(result.instructions)
                        if (varName != result.finalVariable) {
                            result.finalVariable?.let { instructions.add(LLTACElement.assignVariable(varName, varType, it)) }
                        }
                    }

                    else -> lltacError("unexpected lvalue for TAC generation", node)
                }
            }

            is CallFunctionStatementAstNode -> {
                instructions.addAll(
                    expressionLLTACGenerator.buildInstructionsForExpression(
                        variable = null,
                        type = globalContext.unitType,
                        expression = node.expression,
                    ).instructions
                )
            }

            is ForStatementAstNode -> {
                instructions.addAll(visit(node.desugaredContent))
            }

            is IfStatementAstNode -> {
                val condVal = nameEmitter.nextVar()
                val condition = expressionLLTACGenerator.buildInstructionsForExpression(condVal, globalContext.booleanType, node.condition)
                instructions.addAll(condition.instructions)

                val ifNotLabel = LLTACElement.label(nameEmitter.nextLbl()) as LLTACLabel
                instructions.add(LLTACElement.jumpIfNot(ifNotLabel, condition.finalVariable!!, globalContext.booleanType))
                instructions.addAll(visit(node.thenBlock))

                val elseBlock = node.elseBlock
                if (elseBlock == null) {
                    instructions.add(ifNotLabel)
                } else {
                    val endOfThenLabel = LLTACElement.label(nameEmitter.nextLbl()) as LLTACLabel
                    instructions.add(LLTACElement.jump(endOfThenLabel))
                    instructions.add(ifNotLabel)
                    instructions.addAll(visit(elseBlock))
                    instructions.add(endOfThenLabel)
                }
            }

            is ReturnFunctionStatementAstNode -> {
                val expression = node.expression
                if (expression == null) {
                    instructions.add(LLTACElement.ret())
                } else {
                    val variable = nameEmitter.nextVar()
                    val type = expression.tacType()
                    val result = expressionLLTACGenerator.buildInstructionsForExpression(variable, type, expression)
                    instructions.addAll(result.instructions)
                    instructions.add(LLTACElement.ret(result.finalVariable!!, result.finalType))
                }
            }

            is ScalarVariableInitializationASTNode -> {
                val result = expressionLLTACGenerator.buildInstructionsForExpression(
                    variable = node.name,
                    type = node.type,
                    expression = node.expression,
                )
                instructions.addAll(result.instructions)
                if (node.name != result.finalVariable) {
                    instructions.add(LLTACElement.assignVariable(node.name, node.type, result.finalVariable!!))
                }
            }

            is ArrayDeclarationASTNode -> {
                val elementType = node.type.arrayElementType()
                val arrayType = globalContext.arrayOf(elementType)
                instructions.add(LLTACElement.alloc(node.name, arrayType, node.size))

                val initializer = node.expression
                if (initializer is ArrayInitializationExpressionASTNode) {
                    initializer.args.forEachIndexed { index, value ->
                        val indexName = nameEmitter.nextVar()
                        val valueName = nameEmitter.nextVar()
                        instructions.add(LLTACElement.assign(indexName, globalContext.intType, index.toLong()))
                        val result = expressionLLTACGenerator.buildInstructionsForExpression(valueName, elementType, value)
                        instructions.addAll(result.instructions)
                        instructions.add(
                            LLTACElement.store(
                                array = node.name,
                                index = indexName,
                                source = result.finalVariable!!,
                                arrayType = arrayType,
                                elementType = elementType,
                                intType = globalContext.intType,
                            )
                        )
                    }
                }
            }

            is WhileStatementAstNode -> {
                val beginOfWhile = LLTACElement.label(nameEmitter.nextLbl()) as LLTACLabel
                val endOfWhile = LLTACElement.label(nameEmitter.nextLbl()) as LLTACLabel
                instructions.add(beginOfWhile)

                val condition = expressionLLTACGenerator.buildInstructionsForExpression(
                    variable = nameEmitter.nextVar(),
                    type = globalContext.booleanType,
                    expression = node.condition,
                )
                instructions.addAll(condition.instructions)

                instructions.add(LLTACElement.jumpIfNot(endOfWhile, condition.finalVariable!!, globalContext.booleanType))
                instructions.addAll(visit(node.bodyBlock))
                instructions.add(LLTACElement.jump(beginOfWhile))
                instructions.add(endOfWhile)
            }

            is DeclareGlobalVariableASTNode -> {
                node.size?.let { size ->
                    val elementType = node.arrayElementType()
                    val arrayType = globalContext.arrayOf(elementType)
                    val initializer = (node.expression as? ArrayInitializationExpressionASTNode)
                        ?.args
                        ?.map { it.toGlobalInitializerOperand(elementType) }
                    instructions.add(LLTACElement.globalArray(node.name, arrayType, size, initializer))
                }
            }

            is ExpressionASTNode -> lltacError("bare expression is not a TAC statement", node)
            is EmptyNode -> {}
            else -> {}
        }
        return instructions
    }

    private fun mangleFunctionName(function: FunctionDeclAstNode): String =
        function.name

    private fun FunctionArgument.scopeType(): TypeSign =
        if (isArray) globalContext.arrayOf(type) else type

    private fun DeclareGlobalVariableASTNode.arrayElementType(): TypeSign {
        val declaredType = requireNotNull(type) { "global array element type is required" }
        return declaredType.arrayElementType()
    }

    private fun TypeSign.arrayElementType(): TypeSign =
        if (isArray) elementTypeOrNull!! else this

    private fun ExpressionASTNode.tacType(): TypeSign =
        typeTable[this] ?: inferLiteralTacType(this)

    private fun ExpressionASTNode.toGlobalInitializerOperand(expectedType: TypeSign): Operand =
        when (this) {
            is BooleanLiteralExpressionASTNode -> Operand.BoolConst(value)
            is DoubleLiteralExpressionASTNode -> Operand.DoubleConst(value)
            is IntLiteralExpressionASTNode -> Operand.IntConst(value.toLong())
            is StringLiteralExpressionASTNode -> Operand.StringConst(value)
            else -> lltacError("unsupported global ${expectedType.render()} array initializer expression", this)
        }

    private fun inferLiteralTacType(expression: ExpressionASTNode): TypeSign =
        when (expression) {
            is BooleanLiteralExpressionASTNode -> globalContext.booleanType
            is DoubleLiteralExpressionASTNode -> globalContext.doubleType
            is IntLiteralExpressionASTNode -> globalContext.intType
            is StringLiteralExpressionASTNode -> globalContext.stringType
            else -> lltacError("missing semantic type for expression", expression)
        }
}

fun TypeSign.toTACManglePart(): String =
    if (isArray) {
        "Array_${elementTypeOrNull!!.toTACManglePart()}"
    } else {
        name
    }

class TACNameEmitter {
    private var varCounter = 1
    private var lblCounter = 1

    fun nextVar(): String = "var${varCounter++}"
    fun nextLbl(): String = "lbl${lblCounter++}"
}
