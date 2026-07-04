package org.nnezh

import org.nnezh.bred.ast.ASTNode
import org.nnezh.bred.common.FunctionSignature
import org.nnezh.bred.common.TypeSign

enum class SemanticErrorType {
    VARIABLE_OVERSHADOW, VARIABLE_REDECLARATION, UNKNOWN_VARIABLE, VARIABLE_CHANGING_IMMUTABLE, UNEXPECTED_LVALUE,
    FUNCTION_NOT_FOUND, FUNCTION_IS_USED_AS_VARIABLE, REDEFINE_FUNCTION, FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS, COULDNT_RESOLVE_ARGUMENT_TYPE,
    TYPE_CHECKER_INCOMPATIBLE_TYPES, TYPE_CHECKER_INCONSISTENT_ARRAY_TYPE, ARRAY_INDEX_IS_NOT_INTEGER, INVALID_AMOUNT_OF_ARGUMENTS_IN_ARRAYS_INITIALIZATION,
    ARRAY_IS_EXPECTED_BUT_GOT_SCALAR, CONSTRUCTOR_IS_EXPECTED, CONSTRUCTOR_IS_FOUND_BUT_WRONG_ARGUMENTS,
    BLOCK_CONTAINS_MORE_THAN_ONE_RETURN, BLOCK_CONTAINS_CODE_AFTER_RETURN, METHOD_HAS_NO_RETURN, METHOD_HAS_WRONG_RETURN, EXPLICIT_RETURN_IS_EXPECTED
}

sealed interface SemanticDiagnosticDetail {
    data class UnknownVariable(val name: String) : SemanticDiagnosticDetail
    data class VariableRedeclaration(val name: String) : SemanticDiagnosticDetail
    data class ImmutableAssignment(val name: String) : SemanticDiagnosticDetail
    data class TypeMismatch(
        val subject: String,
        val expected: TypeSign?,
        val actual: TypeSign?,
    ) : SemanticDiagnosticDetail
    data class FunctionNotFound(val name: String, val actualArguments: List<TypeSign>) : SemanticDiagnosticDetail
    data class FunctionWrongArguments(
        val name: String,
        val actualArguments: List<TypeSign>,
        val candidates: List<FunctionSignature>,
    ) : SemanticDiagnosticDetail
    data class ArraySizeMismatch(val name: String, val expected: Int, val actual: Int) : SemanticDiagnosticDetail
    data class ArrayIndexType(val name: String, val actual: TypeSign?) : SemanticDiagnosticDetail
    data class ArrayExpected(val name: String, val actual: TypeSign?) : SemanticDiagnosticDetail
    data class WrongReturn(val expected: TypeSign, val actual: TypeSign) : SemanticDiagnosticDetail
    data class ControlFlow(val message: String) : SemanticDiagnosticDetail
    data class UnsupportedNode(val subject: String) : SemanticDiagnosticDetail
}

sealed interface SemanticError {
    val isCriticalError: Boolean
    val detail: SemanticDiagnosticDetail?

    data class VariableScopeSemanticError(
        val where: ASTNode,
        val errorType: SemanticErrorType,
        override val detail: SemanticDiagnosticDetail? = null,
    ): SemanticError {
        override val isCriticalError: Boolean
            get() = true
    }

    data class FunctionSemanticError(
        val where: ASTNode,
        val errorType: SemanticErrorType,
        override val detail: SemanticDiagnosticDetail? = null,
    ): SemanticError {
        override val isCriticalError: Boolean
            get() = true
    }

    data class SemanticWarning(
        val where: ASTNode,
        val type: SemanticErrorType,
        val detail: SemanticDiagnosticDetail? = null,
    )

    data class TypeSemanticError(
        val where: ASTNode,
        val errorType: SemanticErrorType,
        override val detail: SemanticDiagnosticDetail? = null,
    ): SemanticError {
        override val isCriticalError: Boolean
            get() = true
    }

    data class ControlFlowSemanticError(
        val where: ASTNode,
        val critical: Boolean,
        val errorType: SemanticErrorType,
        override val detail: SemanticDiagnosticDetail? = null,
    ): SemanticError {
        override val isCriticalError: Boolean
            get() = critical
    }
}
