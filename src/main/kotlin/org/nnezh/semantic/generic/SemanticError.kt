package org.nnezh.org.nnezh.semantic.generic

import org.nnezh.bred.ast.ASTNode

enum class SemanticErrorType {
    VARIABLE_OVERSHADOW, VARIABLE_REDECLARATION, UNKNOWN_VARIABLE, VARIABLE_CHANGING_IMMUTABLE, UNEXPECTED_LVALUE,
    FUNCTION_NOT_FOUND, FUNCTION_IS_USED_AS_VARIABLE, REDEFINE_FUNCTION, FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS_AMOUNT,
    TYPE_CHECKER_INCOMPATIBLE_TYPES, TYPE_CHECKER_INCONSISTENT_ARRAY_TYPE, ARRAY_INDEX_IS_NOT_INTEGER, INVALID_AMOUNT_OF_ARGUMENTS_IN_ARRAYS_INITIALIZATION,
    ARRAY_IS_EXPECTED_BUT_GOT_SCALAR,
    BLOCK_CONTAINS_MORE_THAN_ONE_RETURN, BLOCK_CONTAINS_CODE_AFTER_RETURN, METHOD_HAS_NO_RETURN, METHOD_HAS_WRONG_RETURN, EXPLICIT_RETURN_IS_EXPECTED
}

sealed interface SemanticError {
    val isCriticalError: Boolean

    data class VariableScopeSemanticError(
        val where: ASTNode,
        val critical: Boolean,
        val errorType: SemanticErrorType
    ): SemanticError {
        override val isCriticalError: Boolean
            get() = critical
    }

    data class FunctionSemanticError(
        val where: ASTNode,
        val critical: Boolean,
        val errorType: SemanticErrorType
    ): SemanticError {
        override val isCriticalError: Boolean
            get() = critical
    }

    data class TypeSemanticError(
        val where: ASTNode,
        val critical: Boolean,
        val errorType: SemanticErrorType
    ): SemanticError {
        override val isCriticalError: Boolean
            get() = critical
    }

    data class ControlFlowSemanticError(
        val where: ASTNode,
        val critical: Boolean,
        val errorType: SemanticErrorType
    ): SemanticError {
        override val isCriticalError: Boolean
            get() = critical
    }


}