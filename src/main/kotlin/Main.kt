package org.nnezh

import org.nnezh.lexer.Lexer
import org.nnezh.lexer.readSource
import arrow.core.raise.either
import org.nnezh.ast.AbstractSyntaxTreeBuilder
import org.nnezh.org.nnezh.ast.AbstractSyntaxTreeExpressionParser
import org.nnezh.org.nnezh.ast.utils.AbstractSyntaxTreeDrawer

fun main(args: Array<String>) {
    val path = args.firstOrNull() ?: "examples/simple.bred"
    val result = either {
        val source = readSource(path).bind()
        val tokens = Lexer(source).tokenize().bind()

        val stringBuilder = StringBuilder()
        stringBuilder.append("Tokens for '$path':")

        for (token in tokens) {
            stringBuilder.append("  ${token.position}\t${token::class.simpleName}\t${token.lexeme}\n")
        }
        stringBuilder.toString()

        AbstractSyntaxTreeBuilder(AbstractSyntaxTreeExpressionParser())
            .build(tokens).bind().let { AbstractSyntaxTreeDrawer().draw(it) }

    }

    result.fold(
        ifLeft = { System.err.println(it) },
        ifRight = { println(it) }
    )
}
