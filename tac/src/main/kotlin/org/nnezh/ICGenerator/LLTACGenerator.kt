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
import org.nnezh.bred.common.BuiltInMethods
import org.nnezh.bred.common.TypeSign
import org.nnezh.org.nnezh.base.Type

class LLTACGenerator(
    private val nameEmitter: TACNameEmitter = TACNameEmitter(),
    private val typeTable: Map<ExpressionASTNode, TypeSign>,
) {
    private val mangledFunctions: MutableMap<String, String> = hashMapOf()
    private val expressionLLTACGenerator = LLTACExpressionSubgenerator(
        nameEmitter = nameEmitter,
        typeTable = typeTable,
        mangledFunctionsCallback = { mangledFunctions },
    )

    fun build(root: ASTNode): List<LLTACElement> =
        visit(root)

    fun visit(node: ASTNode): List<LLTACElement> {
        val instructions = mutableListOf<LLTACElement>()
        when (node) {
            is ProgramRoot -> {
                node.functions.forEach { instructions.addAll(visit(it)) }
            }

            is FunctionDeclAstNode -> {
                val functionName = mangleFunctionName(node)
                mangledFunctions[node.name] = functionName
                instructions.add(LLTACElement.function(functionName, node.result.toTacType()))
                instructions.addAll(node.arguments.map { LLTACElement.getParam(it.name, it.toTacType()) })
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
                            type = Type.IntType,
                            expression = lValue.index,
                        )
                        instructions.addAll(index.instructions)
                        if (indexName != index.finalVariable) {
                            instructions.add(LLTACElement.assignVariable(indexName, Type.IntType, index.finalVariable!!))
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
                                type = rightType,
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

                    else -> error("unexpected lvalue for TAC generation: $lValue")
                }
            }

            is CallFunctionStatementAstNode -> {
                instructions.addAll(
                    expressionLLTACGenerator.buildInstructionsForExpression(
                        variable = null,
                        type = Type.UnitType,
                        expression = node.expression,
                    ).instructions
                )
            }

            is ForStatementAstNode -> {
                instructions.addAll(visit(node.desugaredContent))
            }

            is IfStatementAstNode -> {
                val condVal = nameEmitter.nextVar()
                val condition = expressionLLTACGenerator.buildInstructionsForExpression(condVal, Type.BoolType, node.condition)
                instructions.addAll(condition.instructions)

                val ifNotLabel = LLTACElement.label(nameEmitter.nextLbl()) as LLTACLabel
                instructions.add(LLTACElement.jumpIfNot(ifNotLabel, condition.finalVariable!!))
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
                    type = node.type.toTacType(),
                    expression = node.expression,
                )
                instructions.addAll(result.instructions)
                if (node.name != result.finalVariable) {
                    instructions.add(LLTACElement.assignVariable(node.name, node.type.toTacType(), result.finalVariable!!))
                }
            }

            is ArrayDeclarationASTNode -> {
                val elementType = node.type.toTacType()
                val arrayType = Type.StaticArrayType(elementType, node.size)
                instructions.add(LLTACElement.alloc(node.name, arrayType, node.size))

                val initializer = node.expression
                if (initializer is ArrayInitializationExpressionASTNode) {
                    initializer.args.forEachIndexed { index, value ->
                        val indexName = nameEmitter.nextVar()
                        val valueName = nameEmitter.nextVar()
                        instructions.add(LLTACElement.assign(indexName, Type.IntType, index.toLong()))
                        val result = expressionLLTACGenerator.buildInstructionsForExpression(valueName, elementType, value)
                        instructions.addAll(result.instructions)
                        instructions.add(
                            LLTACElement.store(
                                array = node.name,
                                index = indexName,
                                source = result.finalVariable!!,
                                type = elementType,
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
                    type = Type.BoolType,
                    expression = node.condition,
                )
                instructions.addAll(condition.instructions)

                instructions.add(LLTACElement.jumpIfNot(endOfWhile, condition.finalVariable!!))
                instructions.addAll(visit(node.bodyBlock))
                instructions.add(LLTACElement.jump(beginOfWhile))
                instructions.add(endOfWhile)
            }

            is DeclareGlobalVariableASTNode -> {
                // Global storage is not represented in the current TAC format.
            }

            is ExpressionASTNode -> error("bare expression is not a TAC statement: $node")
            is EmptyNode -> {}
            else -> {}
        }
        return instructions
    }

    private fun mangleFunctionName(function: FunctionDeclAstNode): String {
        if (function.name == "main" && function.name !in BuiltInMethods.functions.map { it.name }) {
            return "main"
        }
        val args = function.arguments.joinToString(separator = "_") { it.toManglePart() }
        return "${function.name}_$args"
    }

    private fun FunctionArgument.toManglePart(): String =
        if (isArray) "Array" else type.toTacType().toManglePart()

    private fun FunctionArgument.toTacType(): Type =
        if (isArray) Type.StaticArrayType(type.toTacType(), 0) else type.toTacType()

    private fun ExpressionASTNode.tacType(): Type =
        typeTable[this]?.toTacType() ?: inferLiteralTacType(this)

    private fun inferLiteralTacType(expression: ExpressionASTNode): Type =
        when (expression) {
            is BooleanLiteralExpressionASTNode -> Type.BoolType
            is DoubleLiteralExpressionASTNode -> Type.DoubleType
            is IntLiteralExpressionASTNode -> Type.IntType
            is StringLiteralExpressionASTNode -> Type.StringType
            else -> error("missing semantic type for expression: $expression")
        }
}

fun TypeSign.toTacType(): Type =
    when (name) {
        "Int" -> Type.IntType
        "String" -> Type.StringType
        "Double" -> Type.DoubleType
        "Boolean" -> Type.BoolType
        "Unit" -> Type.UnitType
        "Array" -> Type.StaticArrayType(args.singleOrNull()?.toTacType() ?: Type.UnitType, 0)
        else -> error("unsupported TAC type: $this")
    }

fun Type.toManglePart(): String =
    when (this) {
        Type.BoolType -> "Boolean"
        Type.DoubleType -> "Double"
        Type.IntType -> "Int"
        is Type.StaticArrayType -> "Array_${elementType.toManglePart()}"
        Type.StringType -> "String"
        Type.UnitType -> "Unit"
    }

class TACNameEmitter {
    private var varCounter = 1
    private var lblCounter = 1

    fun nextVar(): String = "var${varCounter++}"
    fun nextLbl(): String = "lbl${lblCounter++}"
}
