package org.nnezh.bred.ast

import org.nnezh.lexer.Token

data class TokensContext(private val tokens: List<Token>) {
    private var pointer: Int = 0

    fun top(offset: Int = 0): Token {
        return tokens[pointer + offset]
    }

    fun consumeToken(): Token {
        return tokens[pointer++]
    }

    val endOfInput: Boolean
        get() = tokens[pointer] is Token.Eof
}