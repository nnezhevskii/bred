package org.nnezh.org.nnezh.semantic.generic

import org.nnezh.ast.ASTNode

enum class SemanticErrorType {
    VARIABLE_OVERSHADOW, VARIABLE_REDECLARATION, UNKNOWN_VARIABLE
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

}