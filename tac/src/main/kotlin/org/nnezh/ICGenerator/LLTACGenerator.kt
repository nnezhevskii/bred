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
import org.nnezh.bred.ast.DeclareProductTypeASTNode
import org.nnezh.bred.ast.DeclareSumTypeASTNode
import org.nnezh.bred.ast.DeclareTypeASTNode
import org.nnezh.bred.ast.DoubleLiteralExpressionASTNode
import org.nnezh.bred.ast.EmptyNode
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.bred.ast.FieldAccessChainExpressionASTNode
import org.nnezh.bred.ast.ForStatementAstNode
import org.nnezh.bred.ast.FunctionArgument
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.IfStatementAstNode
import org.nnezh.bred.ast.IntLiteralExpressionASTNode
import org.nnezh.bred.ast.LongLiteralExpressionASTNode
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.ast.ReturnFunctionStatementAstNode
import org.nnezh.bred.ast.ScalarVariableInitializationASTNode
import org.nnezh.bred.ast.StringLiteralExpressionASTNode
import org.nnezh.bred.ast.VariableExpressionASTNode
import org.nnezh.bred.ast.WhenExpressionASTNode
import org.nnezh.bred.ast.WhenStatementAstNode
import org.nnezh.bred.ast.WhileStatementAstNode
import org.nnezh.bred.common.TypeSign
import org.nnezh.bred.context.ProgramGlobalContext
import org.nnezh.org.nnezh.ICGenerator.LLTACElement.Companion.endStructDef
import org.nnezh.org.nnezh.ICGenerator.LLTACElement.Companion.field
import org.nnezh.org.nnezh.ICGenerator.LLTACElement.Companion.structDef

class LLTACGenerator(
    private val nameEmitter: TACNameEmitter = TACNameEmitter(),
    private val typeTable: Map<ExpressionASTNode, TypeSign>,
    private val globalContext: ProgramGlobalContext,
) {
    private val sumTagFieldName = "tag"
    private val mangledFunctions: MutableMap<String, String> = hashMapOf()
    private val localVariableTypes: MutableMap<String, TypeSign> = mutableMapOf()
    private val expressionLLTACGenerator = LLTACExpressionSubgenerator(
        nameEmitter = nameEmitter,
        typeTable = typeTable,
        globalContext = globalContext,
        mangledFunctionsCallback = { mangledFunctions },
        variableTypeLookup = { name -> localVariableTypes[name] },
    )

    fun build(root: ASTNode): List<LLTACElement> =
        visit(root)

    fun visit(node: ASTNode): List<LLTACElement> {
        val instructions = mutableListOf<LLTACElement>()
        when (node) {
            is ProgramRoot -> {
                node.types.forEach { instructions.addAll(visit(it)) }
                node.globalVariables.forEach { instructions.addAll(visit(it)) }
                node.functions.forEach { instructions.addAll(visit(it)) }
            }

            is FunctionDeclAstNode -> {
                localVariableTypes.clear()
                val functionName = mangleFunctionName(node)
                mangledFunctions[node.name] = functionName
                instructions.add(LLTACElement.function(functionName, node.result))
                instructions.addAll(node.arguments.map {
                    val type = it.scopeType()
                    localVariableTypes[it.name] = type
                    LLTACElement.getParam(it.name, type)
                })
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

                    is FieldAccessChainExpressionASTNode -> {
                        val rightValueName = nameEmitter.nextVar()
                        val rightType = node.rValue.tacType()
                        val rightValue = expressionLLTACGenerator.buildInstructionsForExpression(
                            variable = rightValueName,
                            type = rightType,
                            expression = node.rValue,
                        )
                        instructions.addAll(rightValue.instructions)
                        instructions.addAll(
                            expressionLLTACGenerator.buildStoreInstructionsForFieldAccess(
                                expression = lValue,
                                source = rightValue.finalVariable!!,
                            )
                        )
                    }

                    else -> lltacError("unexpected lvalue for TAC generation", node)
                }
            }

            is DeclareTypeASTNode -> {
                when (node) {
                    is DeclareProductTypeASTNode -> {
                        val type = globalContext.findType(node.name)!!
                        instructions.add(structDef(type))
                        globalContext.productFields(type).orEmpty().forEach { f ->
                            instructions.add(field(f.key, f.value))
                        }
                        instructions.add(endStructDef())
                    }
                    is DeclareSumTypeASTNode -> {
                        val type = globalContext.findType(node.name)!!
                        val definition = globalContext.sumDefinition(type)
                        if (definition != null) {
                            instructions.add(structDef(type))
                            instructions.add(field(sumTagFieldName, globalContext.intType))
                            definition.variants.forEach { variant ->
                                variant.fields.forEach { f ->
                                    instructions.add(field(sumPayloadFieldName(variant.name, f.name), f.type))
                                }
                            }
                            instructions.add(endStructDef())
                            definition.variants.forEach { variant ->
                                instructions.add(LLTACElement.sumVariant(type, variant.name, variant.tag))
                                variant.fields.forEach { f ->
                                    instructions.add(
                                        LLTACElement.sumVariantField(
                                            ownerType = type,
                                            variantName = variant.name,
                                            fieldName = sumPayloadFieldName(variant.name, f.name),
                                            fieldType = f.type,
                                        )
                                    )
                                }
                            }
                        }
                    }
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
                localVariableTypes[node.name] = node.type
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
                localVariableTypes[node.name] = arrayType
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

            is WhenStatementAstNode -> {
                instructions.addAll(buildWhenStatement(node.expression))
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

    private fun buildWhenStatement(node: WhenExpressionASTNode): List<LLTACElement> {
        val instructions = mutableListOf<LLTACElement>()
        val subjectType = node.subject.tacType()
        val subject = expressionLLTACGenerator.buildInstructionsForExpression(
            variable = nameEmitter.nextVar(),
            type = subjectType,
            expression = node.subject,
        )
        instructions.addAll(subject.instructions)
        val subjectVariable = subject.finalVariable!!
        val sumDefinition = globalContext.sumDefinition(subjectType)
        val endLabel = LLTACElement.label(nameEmitter.nextLbl()) as LLTACLabel

        node.branches.forEach { branch ->
            val condition = branch.condition
            if (condition == null) {
                instructions.addAll(visit(branch.bodyBlock))
                instructions.add(LLTACElement.jump(endLabel))
                return@forEach
            }

            val nextLabel = LLTACElement.label(nameEmitter.nextLbl()) as LLTACLabel
            if (sumDefinition != null && condition is VariableExpressionASTNode) {
                val variant = sumDefinition.variants.single { it.name == condition.token.lexeme }
                val actualTag = nameEmitter.nextVar()
                val expectedTag = nameEmitter.nextVar()
                val conditionVariable = nameEmitter.nextVar()
                instructions.add(
                    LLTACElement.loadField(
                        ownerPath = subjectVariable,
                        ownerType = subjectType,
                        fieldName = sumTagFieldName,
                        fieldType = globalContext.intType,
                        destination = actualTag,
                    )
                )
                instructions.add(LLTACElement.assign(expectedTag, globalContext.intType, variant.tag.toLong()))
                instructions.add(
                    LLTACElement.binOp(
                        opcode = LLTACOperation.LLTAC_EQ,
                        varName = conditionVariable,
                        varType = globalContext.booleanType,
                        arg1Name = actualTag,
                        arg1Type = globalContext.intType,
                        arg2Name = expectedTag,
                        arg2Type = globalContext.intType,
                    )
                )
                instructions.add(LLTACElement.jumpIfNot(nextLabel, conditionVariable, globalContext.booleanType))
            } else {
                val branchValue = expressionLLTACGenerator.buildInstructionsForExpression(
                    variable = nameEmitter.nextVar(),
                    type = condition.tacType(),
                    expression = condition,
                )
                val conditionVariable = nameEmitter.nextVar()
                instructions.addAll(branchValue.instructions)
                instructions.add(
                    LLTACElement.binOp(
                        opcode = LLTACOperation.LLTAC_EQ,
                        varName = conditionVariable,
                        varType = globalContext.booleanType,
                        arg1Name = subjectVariable,
                        arg1Type = subjectType,
                        arg2Name = branchValue.finalVariable!!,
                        arg2Type = branchValue.finalType,
                    )
                )
                instructions.add(LLTACElement.jumpIfNot(nextLabel, conditionVariable, globalContext.booleanType))
            }

            instructions.addAll(visit(branch.bodyBlock))
            instructions.add(LLTACElement.jump(endLabel))
            instructions.add(nextLabel)
        }

        instructions.add(endLabel)
        return instructions
    }

    private fun FunctionArgument.scopeType(): TypeSign =
        if (isArray) globalContext.arrayOf(type) else type

    private fun DeclareGlobalVariableASTNode.arrayElementType(): TypeSign {
        val declaredType = requireNotNull(type) { "global array element type is required" }
        return declaredType.arrayElementType()
    }

    private fun TypeSign.arrayElementType(): TypeSign =
        if (isArray) elementTypeOrNull!! else this

    private fun sumPayloadFieldName(variantName: String, fieldName: String): String =
        "${variantName}_${fieldName}"

    private fun ExpressionASTNode.tacType(): TypeSign =
        typeTable[this] ?: inferLiteralTacType(this)

    private fun ExpressionASTNode.toGlobalInitializerOperand(expectedType: TypeSign): Operand =
        when (this) {
            is BooleanLiteralExpressionASTNode -> Operand.BoolConst(value)
            is DoubleLiteralExpressionASTNode -> Operand.DoubleConst(value)
            is IntLiteralExpressionASTNode -> Operand.IntConst(value.toLong())
            is LongLiteralExpressionASTNode -> Operand.LongConst(value)
            is StringLiteralExpressionASTNode -> Operand.StringConst(value)
            else -> lltacError("unsupported global ${expectedType.render()} array initializer expression", this)
        }

    private fun inferLiteralTacType(expression: ExpressionASTNode): TypeSign =
        when (expression) {
            is BooleanLiteralExpressionASTNode -> globalContext.booleanType
            is DoubleLiteralExpressionASTNode -> globalContext.doubleType
            is IntLiteralExpressionASTNode -> globalContext.intType
            is LongLiteralExpressionASTNode -> globalContext.longType
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
