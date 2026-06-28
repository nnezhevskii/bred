/*
 * This file was generated with the assistance of AI (Cursor).
 */

package org.nnezh.lexer

/**
 * Typed lexical errors. Returned via [arrow.core.Either] from the lexer instead
 * of being thrown, so callers handle them explicitly.
 */
sealed interface LexerError {
    val position: Position
    val message: String

    data class UnexpectedCharacter(
        override val position: Position,
        val char: Char,
        val hint: String? = null,
    ) : LexerError {
        override val message: String
            get() = "Unexpected character '$char'" + (hint?.let { " ($it)" }.orEmpty())
    }

    data class UnterminatedString(override val position: Position) : LexerError {
        override val message: String get() = "Unterminated string literal"
    }

    data class UnterminatedBlockComment(override val position: Position) : LexerError {
        override val message: String get() = "Unterminated block comment"
    }

    data class UnknownEscape(override val position: Position, val escape: Char) : LexerError {
        override val message: String get() = "Unknown escape sequence: \\$escape"
    }

    data class InvalidNumber(override val position: Position, val reason: String) : LexerError {
        override val message: String get() = reason
    }
}
