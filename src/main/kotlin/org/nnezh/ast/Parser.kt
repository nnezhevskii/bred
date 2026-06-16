package org.nnezh.org.nnezh.ast

import arrow.core.raise.Raise
import org.nnezh.ast.ASTNode

interface Parser<out T : ASTNode> {
    fun Raise<ASTError>.parse(context: TokensContext): T
}

fun <T : ASTNode> Raise<ASTError>.parseWith(parser: Parser<T>, context: TokensContext): T =
    with(parser) { parse(context) }
