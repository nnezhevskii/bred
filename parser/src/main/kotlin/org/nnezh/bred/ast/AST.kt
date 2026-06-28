package org.nnezh.bred.ast

data class ProgramRoot(
    val functions: MutableList<FunctionDeclAstNode>,
    val globalVariables: List<DeclareGlobalVariableASTNode>,
    val types: List<DeclareTypeASTNode>,
    val typeClasses: List<TypeClassDeclAstNode> = emptyList(),
    val instances: List<InstanceDeclAstNode> = emptyList(),
) : ASTNode

data class DeclareGlobalVariableASTNode(
    val name: String,
    val type: TypeSign? = null,
    val expression: ExpressionASTNode? = null,
    val isMutable: Boolean = false,
    val size: Int? = null,
) : ASTNode

data class DeclareTypeASTNode(val name: String) : ASTNode

data class TypeSign(
    val name: String,
    val args: List<TypeSign> = emptyList(),
)

data class FunctionDeclAstNode(
    val name: String,
    val genericParams: List<GenericParam>,
    val arguments: List<FunctionArgument>,
    val result: TypeSign,
    val body: BlockAstNode,
) : ASTNode

data class GenericParam(val name: String, val constraints: List<String>)
data class FunctionArgument(
    val name: String,
    val type: TypeSign,
    val isArray: Boolean = false,
)

data class BlockAstNode(val statements: List<StatementAstNode>) : ASTNode

sealed interface StatementAstNode : ASTNode

sealed interface DeclareVariableASTNode : StatementAstNode

data class ScalarVariableInitializationASTNode(
    val name: String,
    val type: TypeSign,
    val expression: ExpressionASTNode,
    val isMutable: Boolean,
) : DeclareVariableASTNode

data class ArrayDeclarationASTNode(
    val name: String,
    val type: TypeSign,
    val size: Int,
    val expression: ExpressionASTNode?,
    val isMutable: Boolean,
) : DeclareVariableASTNode

data class AssignmentStatementAstNode(
    val lValue: ExpressionASTNode,
    val rValue: ExpressionASTNode,
) : StatementAstNode

data class CallFunctionStatementAstNode(
    val expression: ExpressionASTNode,
) : StatementAstNode

data class ReturnFunctionStatementAstNode(
    val expression: ExpressionASTNode?,
    val explicit: Boolean,
) : StatementAstNode

data class IfStatementAstNode(
    val condition: ExpressionASTNode,
    val thenBlock: BlockAstNode,
    val elseBlock: BlockAstNode?,
) : StatementAstNode

data class WhileStatementAstNode(
    val condition: ExpressionASTNode,
    val bodyBlock: BlockAstNode,
) : StatementAstNode

data class ForStatementAstNode(
    val desugaredContent: BlockAstNode,
) : StatementAstNode

data class TypeClassDeclAstNode(
    val name: String,
    val genericParam: GenericParam,
    val methods: List<TypeClassMethodDeclAstNode>,
) : ASTNode

data class TypeClassMethodDeclAstNode(
    val name: String,
    val arguments: List<FunctionArgument>,
    val result: TypeSign,
) : ASTNode

data class InstanceDeclAstNode(
    val typeClassName: String,
    val type: TypeSign,
    val methods: List<FunctionDeclAstNode>,
) : ASTNode
