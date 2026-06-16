package org.nnezh.org.nnezh.ast

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.Type

public inline fun <reified T : Token> Raise<ASTError>.match(actual: Token, onError: (Token) -> ASTError): T {
    ensure(actual is T) { onError(actual) }
    return actual
}

fun Raise<ASTError>.parseType(typeToken: Token.Identifier): Type =
    Type.fromString(typeToken.lexeme).fold(
        ifLeft = { raise(AstErrorFactory.invalidType(typeToken)) },
        ifRight = { it },
    )