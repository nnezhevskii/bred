/*
 * This file was generated with the assistance of AI (Cursor).
 */

package org.nnezh.lexer

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either

/**
 * Hand-written lexical analyzer for the bred language.
 *
 * Scans the [source] text character by character and produces a flat list of
 * [Token]s. The list always ends with a single [Token.Eof].
 *
 * Whitespace, line comments (`// ...`) and block comments (`/* ... */`) are
 * skipped and never produce tokens.
 *
 * Malformed input is reported as a [LexerError] on the left side of the
 * returned [Either] instead of being thrown.
 */
class Lexer(private val source: String) {

    private var index: Int = 0
    private var line: Int = 1
    private var column: Int = 1

    /** Tokenizes the whole source, returning either the tokens or the first error. */
    fun tokenize(): Either<LexerError, List<Token>> = either {
        val tokens = mutableListOf<Token>()
        while (true) {
            skipWhitespaceAndComments()
            if (isAtEnd()) break
            tokens += scanToken()
        }
        tokens += Token.Eof(currentPosition())
        tokens
    }

    private fun Raise<LexerError>.scanToken(): Token {
        val char = peek()
        return when {
            char.isLetter() || char == '_' -> scanIdentifierOrKeyword()
            char.isDigit() -> scanNumber()
            char == '"' -> scanString()
            else -> scanOperatorOrPunctuation()
        }
    }

    private fun scanIdentifierOrKeyword(): Token {
        val start = currentPosition()
        val builder = StringBuilder()
        while (!isAtEnd() && (peek().isLetterOrDigit() || peek() == '_')) {
            builder.append(advance())
        }
        val text = builder.toString()
        val keywordFactory = Token.Keyword.byText[text]
        return keywordFactory?.invoke(start) ?: Token.Identifier(text, start)
    }

    private fun Raise<LexerError>.scanNumber(): Token {
        val start = currentPosition()
        val builder = StringBuilder()
        while (!isAtEnd() && peek().isDigit()) {
            builder.append(advance())
        }

        // A fractional part only applies when a digit follows the dot; otherwise
        // the '.' is a standalone Punctuation.Dot token.
        var isDouble = false
        if (!isAtEnd() && peek() == '.' && peekNext().isDigit()) {
            isDouble = true
            builder.append(advance()) // '.'
            while (!isAtEnd() && peek().isDigit()) {
                builder.append(advance())
            }
        }

        // Reject identifiers glued to a number, e.g. "12abc" or "3.14abc".
        if (!isAtEnd() && (peek().isLetter() || peek() == '_')) {
            raise(LexerError.InvalidNumber(currentPosition(), "Invalid number literal: unexpected '${peek()}'"))
        }

        val text = builder.toString()
        return if (isDouble) {
            val value = text.toDoubleOrNull()
                ?: raise(LexerError.InvalidNumber(start, "Invalid double literal: $text"))
            Token.Literal.DoubleLiteral(value, text, start)
        } else {
            val value = text.toLongOrNull()
                ?: raise(LexerError.InvalidNumber(start, "Integer literal out of range: $text"))
            Token.Literal.IntLiteral(value, text, start)
        }
    }

    private fun Raise<LexerError>.scanString(): Token {
        val start = currentPosition()
        val raw = StringBuilder()
        val decoded = StringBuilder()
        raw.append(advance()) // opening quote
        while (true) {
            if (isAtEnd()) {
                raise(LexerError.UnterminatedString(start))
            }
            val char = peek()
            when (char) {
                '"' -> {
                    raw.append(advance()) // closing quote
                    return Token.Literal.StringLiteral(decoded.toString(), raw.toString(), start)
                }
                '\n' -> raise(LexerError.UnterminatedString(start))
                '\\' -> {
                    raw.append(advance()) // backslash
                    if (isAtEnd()) {
                        raise(LexerError.UnterminatedString(start))
                    }
                    val escape = advance()
                    raw.append(escape)
                    decoded.append(decodeEscape(escape))
                }
                else -> {
                    raw.append(char)
                    decoded.append(advance())
                }
            }
        }
    }

    private fun Raise<LexerError>.decodeEscape(escape: Char): Char = when (escape) {
        'n' -> '\n'
        't' -> '\t'
        'r' -> '\r'
        '"' -> '"'
        '\\' -> '\\'
        '0' -> '\u0000'
        else -> raise(LexerError.UnknownEscape(currentPosition(), escape))
    }

    private fun Raise<LexerError>.scanOperatorOrPunctuation(): Token {
        val start = currentPosition()
        return when (val char = advance()) {
            '+' -> Token.Operator.Plus(start)
            '-' -> Token.Operator.Minus(start)
            '*' -> Token.Operator.Star(start)
            '/' -> Token.Operator.Slash(start)
            '%' -> Token.Operator.Percent(start)
            '(' -> Token.Punctuation.LParen(start)
            ')' -> Token.Punctuation.RParen(start)
            '{' -> Token.Punctuation.LBrace(start)
            '}' -> Token.Punctuation.RBrace(start)
            ',' -> Token.Punctuation.Comma(start)
            ':' -> Token.Punctuation.Colon(start)
            ';' -> raise(LexerError.UnexpectedCharacter(start, ';'))
            '.' -> Token.Punctuation.Dot(start)
            '=' -> if (match('=')) Token.Operator.Eq(start) else Token.Operator.Assign(start)
            '!' -> if (match('=')) Token.Operator.Neq(start) else Token.Operator.Not(start)
            '<' -> if (match('=')) Token.Operator.Le(start) else Token.Operator.Lt(start)
            '>' -> if (match('=')) Token.Operator.Ge(start) else Token.Operator.Gt(start)
            '&' -> if (match('&')) {
                Token.Operator.And(start)
            } else {
                raise(LexerError.UnexpectedCharacter(start, '&', "did you mean '&&'?"))
            }
            '|' -> if (match('|')) {
                Token.Operator.Or(start)
            } else {
                raise(LexerError.UnexpectedCharacter(start, '|', "did you mean '||'?"))
            }
            else -> raise(LexerError.UnexpectedCharacter(start, char))
        }
    }

    private fun Raise<LexerError>.skipWhitespaceAndComments() {
        while (!isAtEnd()) {
            val char = peek()
            when {
                char.isWhitespace() -> advance()
                char == '/' && peekNext() == '/' -> skipLineComment()
                char == '/' && peekNext() == '*' -> skipBlockComment()
                else -> return
            }
        }
    }

    private fun skipLineComment() {
        while (!isAtEnd() && peek() != '\n') {
            advance()
        }
    }

    private fun Raise<LexerError>.skipBlockComment() {
        val start = currentPosition()
        advance() // '/'
        advance() // '*'
        while (true) {
            if (isAtEnd()) {
                raise(LexerError.UnterminatedBlockComment(start))
            }
            if (peek() == '*' && peekNext() == '/') {
                advance() // '*'
                advance() // '/'
                return
            }
            advance()
        }
    }

    // region Low-level cursor helpers
    private fun isAtEnd(): Boolean = index >= source.length

    private fun peek(): Char = source[index]

    private fun peekNext(): Char =
        if (index + 1 < source.length) source[index + 1] else '\u0000'

    /** Consumes and returns the current character, updating line/column. */
    private fun advance(): Char {
        val char = source[index]
        index++
        if (char == '\n') {
            line++
            column = 1
        } else {
            column++
        }
        return char
    }

    /** Consumes [expected] if it is the current character; reports success. */
    private fun match(expected: Char): Boolean {
        if (isAtEnd() || peek() != expected) return false
        advance()
        return true
    }

    private fun currentPosition(): Position = Position(line, column)
    // endregion
}
