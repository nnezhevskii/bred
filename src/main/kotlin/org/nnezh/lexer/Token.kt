/*
 * This file was generated with the assistance of AI (Cursor).
 */

package org.nnezh.lexer

/**
 * Position of a token in the source file. Both [line] and [column] are 1-based.
 */
data class Position(val line: Int, val column: Int) {
    override fun toString(): String = "$line:$column"
}

/**
 * Lexeme hierarchy for the bred language.
 *
 * Every token carries its raw [lexeme] text and the [position] where it starts
 * in the source file. The hierarchy is organised into logical groups
 * (keywords, literals, operators, punctuation) so later compiler stages can
 * pattern-match on broad categories or concrete token types.
 */
sealed class Token {
    abstract val lexeme: String
    abstract val position: Position

    // region Keywords
    sealed class Keyword : Token() {
        data class Fun(override val position: Position) : Keyword() {
            override val lexeme: String = "fun"
        }

        data class Val(override val position: Position) : Keyword() {
            override val lexeme: String = "val"
        }

        data class Var(override val position: Position) : Keyword() {
            override val lexeme: String = "var"
        }

        data class If(override val position: Position) : Keyword() {
            override val lexeme: String = "if"
        }

        data class Else(override val position: Position) : Keyword() {
            override val lexeme: String = "else"
        }

        data class Return(override val position: Position) : Keyword() {
            override val lexeme: String = "return"
        }

        data class While(override val position: Position) : Keyword() {
            override val lexeme: String = "while"
        }

        data class True(override val position: Position) : Keyword() {
            override val lexeme: String = "true"
        }

        data class False(override val position: Position) : Keyword() {
            override val lexeme: String = "false"
        }

        companion object {
            /** Maps reserved words to their token factories. */
            val byText: Map<String, (Position) -> Keyword> = mapOf(
                "fun" to ::Fun,
                "val" to ::Val,
                "var" to ::Var,
                "if" to ::If,
                "else" to ::Else,
                "return" to ::Return,
                "while" to ::While,
                "true" to ::True,
                "false" to ::False,
            )
        }
    }
    // endregion

    /** A user-defined name (variable, function, type, ...). */
    data class Identifier(
        override val lexeme: String,
        override val position: Position,
    ) : Token()

    // region Literals
    sealed class Literal : Token() {
        data class IntLiteral(
            val value: Long,
            override val lexeme: String,
            override val position: Position,
        ) : Literal()

        data class DoubleLiteral(
            val value: Double,
            override val lexeme: String,
            override val position: Position,
        ) : Literal()

        /**
         * String literal. [value] is the decoded content (escape sequences
         * resolved), while [lexeme] keeps the raw text including quotes.
         */
        data class StringLiteral(
            val value: String,
            override val lexeme: String,
            override val position: Position,
        ) : Literal()
    }
    // endregion

    // region Operators
    sealed class Operator : Token() {
        data class Plus(override val position: Position) : Operator() {
            override val lexeme: String = "+"
        }

        data class Minus(override val position: Position) : Operator() {
            override val lexeme: String = "-"
        }

        data class Star(override val position: Position) : Operator() {
            override val lexeme: String = "*"
        }

        data class Slash(override val position: Position) : Operator() {
            override val lexeme: String = "/"
        }

        data class Percent(override val position: Position) : Operator() {
            override val lexeme: String = "%"
        }

        data class Assign(override val position: Position) : Operator() {
            override val lexeme: String = "="
        }

        data class Eq(override val position: Position) : Operator() {
            override val lexeme: String = "=="
        }

        data class Neq(override val position: Position) : Operator() {
            override val lexeme: String = "!="
        }

        data class Lt(override val position: Position) : Operator() {
            override val lexeme: String = "<"
        }

        data class Gt(override val position: Position) : Operator() {
            override val lexeme: String = ">"
        }

        data class Le(override val position: Position) : Operator() {
            override val lexeme: String = "<="
        }

        data class Ge(override val position: Position) : Operator() {
            override val lexeme: String = ">="
        }

        data class And(override val position: Position) : Operator() {
            override val lexeme: String = "&&"
        }

        data class Or(override val position: Position) : Operator() {
            override val lexeme: String = "||"
        }

        data class Not(override val position: Position) : Operator() {
            override val lexeme: String = "!"
        }
    }
    // endregion

    // region Punctuation
    sealed class Punctuation : Token() {
        data class LParen(override val position: Position) : Punctuation() {
            override val lexeme: String = "("
        }

        data class RParen(override val position: Position) : Punctuation() {
            override val lexeme: String = ")"
        }

        data class LBrace(override val position: Position) : Punctuation() {
            override val lexeme: String = "{"
        }

        data class RBrace(override val position: Position) : Punctuation() {
            override val lexeme: String = "}"
        }

        data class Comma(override val position: Position) : Punctuation() {
            override val lexeme: String = ","
        }

        data class Colon(override val position: Position) : Punctuation() {
            override val lexeme: String = ":"
        }

        data class Semicolon(override val position: Position) : Punctuation() {
            override val lexeme: String = ";"
        }

        data class Dot(override val position: Position) : Punctuation() {
            override val lexeme: String = "."
        }
    }
    // endregion

    /** Marks the end of the token stream. */
    data class Eof(override val position: Position) : Token() {
        override val lexeme: String = ""
    }
}
