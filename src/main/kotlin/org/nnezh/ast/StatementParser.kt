package org.nnezh.org.nnezh.ast

import arrow.core.raise.Raise
import org.nnezh.ast.StatementASTNode
import org.nnezh.lexer.Token

class StatementParser(
    private val ifParser: Parser<StatementASTNode>,
    private val whileParser: Parser<StatementASTNode>,
    private val forParser: Parser<StatementASTNode>,
    private val immutableInitializationParser: Parser<StatementASTNode>,
    private val assignParser: Parser<StatementASTNode>,
    private val callParser: Parser<StatementASTNode>,
) : Parser<StatementASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): StatementASTNode =
        when (context.top()) {
            is Token.Keyword.Val -> parseWith(immutableInitializationParser, context)

            is Token.Identifier -> {
                if (context.top(1) is Token.Punctuation.LParen) {
                    parseWith(callParser, context)
                } else {
                    parseWith(assignParser, context)
                }
            }

            is Token.Keyword.If -> parseWith(ifParser, context)
            is Token.Keyword.While -> parseWith(whileParser, context)
            is Token.Keyword.For -> parseWith(forParser, context)

            else -> raise(ASTError("Didn't expect ${context.top()} in ${context.top().position}"))
        }
}
