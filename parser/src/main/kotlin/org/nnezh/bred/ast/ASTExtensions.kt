package org.nnezh.bred.ast

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import org.nnezh.lexer.Token

public inline fun <reified T : Token> Raise<ASTError>.match(actual: Token, onError: (Token) -> ASTError): T {
    ensure(actual is T) { onError(actual) }
    return actual
}
