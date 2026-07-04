package org.nnezh

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
import org.nnezh.bred.ast.DoubleLiteralExpressionASTNode
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.bred.ast.FieldAccessChainExpressionASTNode
import org.nnezh.bred.ast.FunctionCallExpressionASTNode
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.IfStatementAstNode
import org.nnezh.bred.ast.IntLiteralExpressionASTNode
import org.nnezh.bred.ast.LongLiteralExpressionASTNode
import org.nnezh.bred.ast.ReturnFunctionStatementAstNode
import org.nnezh.bred.ast.ScalarVariableInitializationASTNode
import org.nnezh.bred.ast.StringLiteralExpressionASTNode
import org.nnezh.bred.ast.UnaryExpressionASTNode
import org.nnezh.bred.ast.VariableExpressionASTNode
import org.nnezh.bred.ast.WhileStatementAstNode
import org.nnezh.bred.common.FunctionSignature
import org.nnezh.bred.common.TypeSign
import org.nnezh.lexer.Position

class SemanticDiagnosticFormatter {
    fun format(error: SemanticError): String {
        val type = error.type()
        val location = error.where().position()?.let { " at $it" }.orEmpty()
        return "Semantic error $type$location: ${message(type, error.where(), error.detail)}"
    }

    fun format(warning: SemanticError.SemanticWarning): String {
        val location = warning.where.position()?.let { " at $it" }.orEmpty()
        return "Semantic warning ${warning.type}$location: ${message(warning.type, warning.where, warning.detail)}"
    }

    private fun message(
        type: SemanticErrorType,
        where: ASTNode,
        detail: SemanticDiagnosticDetail?,
    ): String =
        when (detail) {
            is SemanticDiagnosticDetail.UnknownVariable ->
                "unknown variable '${detail.name}'."
            is SemanticDiagnosticDetail.VariableRedeclaration ->
                "variable '${detail.name}' is already declared in this scope."
            is SemanticDiagnosticDetail.ImmutableAssignment ->
                "cannot assign to immutable variable '${detail.name}'."
            is SemanticDiagnosticDetail.TypeMismatch ->
                "cannot ${detail.subject}${typePart(detail.expected, detail.actual)}."
            is SemanticDiagnosticDetail.FunctionNotFound ->
                "function '${detail.name}' is not declared for arguments ${detail.actualArguments.renderArguments()}."
            is SemanticDiagnosticDetail.FunctionWrongArguments ->
                "no overload of '${detail.name}' accepts ${detail.actualArguments.renderArguments()}; candidates: ${detail.candidates.renderCandidates()}."
            is SemanticDiagnosticDetail.ArraySizeMismatch ->
                "array '${detail.name}' expects ${detail.expected} initializer element(s), got ${detail.actual}."
            is SemanticDiagnosticDetail.ArrayIndexType ->
                "array '${detail.name}' index must be Int, got ${detail.actual.renderNullable()}."
            is SemanticDiagnosticDetail.ArrayExpected ->
                "variable '${detail.name}' must be an array for indexed access, got ${detail.actual.renderNullable()}."
            is SemanticDiagnosticDetail.WrongReturn ->
                "return type mismatch: expected ${detail.expected.render()}, got ${detail.actual.render()}."
            is SemanticDiagnosticDetail.ControlFlow ->
                detail.message
            is SemanticDiagnosticDetail.UnsupportedNode ->
                "unsupported semantic node: ${detail.subject}."
            null -> fallbackMessage(type, where)
        }

    private fun fallbackMessage(type: SemanticErrorType, where: ASTNode): String =
        when (type) {
            SemanticErrorType.UNKNOWN_VARIABLE -> "unknown variable in ${where.summary()}."
            SemanticErrorType.VARIABLE_REDECLARATION -> "variable is already declared in ${where.summary()}."
            SemanticErrorType.VARIABLE_CHANGING_IMMUTABLE -> "cannot assign to immutable value in ${where.summary()}."
            SemanticErrorType.FUNCTION_NOT_FOUND -> "function is not declared in ${where.summary()}."
            SemanticErrorType.FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS -> "function exists, but no overload accepts these argument types in ${where.summary()}."
            SemanticErrorType.TYPE_CHECKER_INCOMPATIBLE_TYPES -> "incompatible types in ${where.summary()}."
            SemanticErrorType.TYPE_CHECKER_INCONSISTENT_ARRAY_TYPE -> "array initializer contains inconsistent element types."
            SemanticErrorType.ARRAY_INDEX_IS_NOT_INTEGER -> "array index must be Int in ${where.summary()}."
            SemanticErrorType.INVALID_AMOUNT_OF_ARGUMENTS_IN_ARRAYS_INITIALIZATION -> "array initializer size does not match declaration in ${where.summary()}."
            SemanticErrorType.ARRAY_IS_EXPECTED_BUT_GOT_SCALAR -> "array access was used on a scalar value in ${where.summary()}."
            SemanticErrorType.METHOD_HAS_WRONG_RETURN -> "return expression has the wrong type in ${where.summary()}."
            SemanticErrorType.EXPLICIT_RETURN_IS_EXPECTED -> "non-Unit function must return a value on all paths."
            SemanticErrorType.BLOCK_CONTAINS_CODE_AFTER_RETURN -> "block contains code after a guaranteed return."
            SemanticErrorType.BLOCK_CONTAINS_MORE_THAN_ONE_RETURN -> "block contains more than one reachable return path in sequence."
            else -> "${type.name.lowercase().replace('_', ' ')} in ${where.summary()}."
        }

    private fun typePart(expected: TypeSign?, actual: TypeSign?): String =
        when {
            expected != null && actual != null -> ": expected ${expected.render()}, got ${actual.render()}"
            expected != null -> ": expected ${expected.render()}"
            actual != null -> ": got ${actual.render()}"
            else -> ""
        }

    private fun SemanticError.type(): SemanticErrorType =
        when (this) {
            is SemanticError.ControlFlowSemanticError -> errorType
            is SemanticError.FunctionSemanticError -> errorType
            is SemanticError.TypeSemanticError -> errorType
            is SemanticError.VariableScopeSemanticError -> errorType
        }

    private fun SemanticError.where(): ASTNode =
        when (this) {
            is SemanticError.ControlFlowSemanticError -> where
            is SemanticError.FunctionSemanticError -> where
            is SemanticError.TypeSemanticError -> where
            is SemanticError.VariableScopeSemanticError -> where
        }

    private fun ASTNode.position(): Position? =
        when (this) {
            is VariableExpressionASTNode -> token.position
            is BinaryExpressionASTNode -> operator.position
            is UnaryExpressionASTNode -> operator.position
            is ArrayElementAccessASTNode -> index.position()
            is FunctionCallExpressionASTNode -> arguments.firstNotNullOfOrNull { it.position() }
            is ScalarVariableInitializationASTNode -> expression.position()
            is DeclareGlobalVariableASTNode -> expression?.position()
            is ArrayDeclarationASTNode -> expression?.position()
            is AssignmentStatementAstNode -> lValue.position() ?: rValue.position()
            is CallFunctionStatementAstNode -> expression.position()
            is ReturnFunctionStatementAstNode -> expression?.position()
            is IfStatementAstNode -> condition.position()
            is WhileStatementAstNode -> condition.position()
            else -> null
        }

    private fun ASTNode.summary(): String =
        when (this) {
            is FunctionDeclAstNode -> "function '${name}'"
            is ScalarVariableInitializationASTNode -> "initializer for '${name}: ${type.render()}'"
            is DeclareGlobalVariableASTNode -> "global '${name}'"
            is ArrayDeclarationASTNode -> "array declaration '${name}: ${type.render()}[$size]'"
            is AssignmentStatementAstNode -> "assignment '${lValue.expressionSummary()} = ${rValue.expressionSummary()}'"
            is FunctionCallExpressionASTNode -> "call '${name}(...)'"
            is VariableExpressionASTNode -> "variable '${token.lexeme}'"
            is ArrayElementAccessASTNode -> "array access '${name}[...]'"
            is BinaryExpressionASTNode -> "binary expression '${left.expressionSummary()} ${operator.lexeme} ${right.expressionSummary()}'"
            is UnaryExpressionASTNode -> "unary expression '${operator.lexeme}${operand.expressionSummary()}'"
            is ReturnFunctionStatementAstNode -> "return statement"
            is BlockAstNode -> "block"
            else -> this::class.simpleName ?: "AST node"
        }

    private fun ExpressionASTNode.expressionSummary(): String =
        when (this) {
            is IntLiteralExpressionASTNode -> value.toString()
            is LongLiteralExpressionASTNode -> "${value}L"
            is DoubleLiteralExpressionASTNode -> value.toString()
            is BooleanLiteralExpressionASTNode -> value.toString()
            is StringLiteralExpressionASTNode -> "\"${value.escape()}\""
            is VariableExpressionASTNode -> token.lexeme
            is ArrayElementAccessASTNode -> "$name[${index.expressionSummary()}]"
            is FunctionCallExpressionASTNode -> "$name(${arguments.joinToString(", ") { it.expressionSummary() }})"
            is ArrayInitializationExpressionASTNode -> "[${args.joinToString(", ") { it.expressionSummary() }}]"
            is BinaryExpressionASTNode -> "${left.expressionSummary()} ${operator.lexeme} ${right.expressionSummary()}"
            is UnaryExpressionASTNode -> "${operator.lexeme}${operand.expressionSummary()}"
            is FieldAccessChainExpressionASTNode -> this.root + this.next?.let { n -> "." + n.expressionSummary() }
        }

    private fun String.escape(): String =
        replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\"", "\\\"")

    private fun TypeSign?.renderNullable(): String =
        this?.render() ?: "unknown"

    private fun List<TypeSign>.renderArguments(): String =
        joinToString(prefix = "(", postfix = ")") { it.render() }

    private fun List<FunctionSignature>.renderCandidates(): String =
        if (isEmpty()) {
            "<none>"
        } else {
            joinToString("; ") { it.render() }
        }

    private fun FunctionSignature.render(): String =
        "$name${args.renderArguments()}: ${resultType.render()}"

}
