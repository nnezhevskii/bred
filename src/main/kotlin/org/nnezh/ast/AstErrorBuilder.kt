package org.nnezh.org.nnezh.ast

import org.nnezh.lexer.Token
import arrow.core.Either

object AstErrorFactory {
    fun expectedFunOrVariableDeclarationError(token: Token): ASTError = ASTError("Unexpected token type at ${token.position}. Expected function or constant declaration but got ${token.lexeme}")
    fun expectedFunDeclarationError(token: Token): ASTError = ASTError("Unexpected token type at ${token.position}. Expected function declaration but got ${token.lexeme}")
    fun unexpectedEOF(token: Token) = ASTError("Unexpected EOF at ${token.position}")
    fun expectedExpression(token: Token): ASTError = ASTError("Expected expression but got ${token.lexeme} at ${token.position}")
    fun expectedClosingParen(token: Token): ASTError = ASTError("Expected ')' but got ${token.lexeme} at ${token.position}")
    fun expectedArgumentSeparator(token: Token): ASTError = ASTError("Expected ',' or ')' but got ${token.lexeme} at ${token.position}")
}