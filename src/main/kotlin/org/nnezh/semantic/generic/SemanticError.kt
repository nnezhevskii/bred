package org.nnezh.org.nnezh.semantic.generic

import org.nnezh.ast.ASTNode

enum class SemanticErrorType {
    VARIABLE_OVERSHADOW, VARIABLE_REDECLARATION, UNKNOWN_VARIABLE,
    FUNCTION_NOT_FOUND, FUNCTION_IS_USED_AS_VARIABLE, REDEFINE_FUNCTION, FUNCTION_EXISTS_BUT_WRONG_ARGUMENTS_AMOUNT,
    TYPE_CHECKER_INCOMPATIBLE_TYPES
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

}