package org.nnezh.bred.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.Parser
import org.nnezh.bred.ast.ReturnFunctionStatementAstNode
import org.nnezh.bred.ast.StatementAstNode
import org.nnezh.bred.ast.TokensContext
import org.nnezh.bred.ast.parseWith
import org.nnezh.lexer.Token

class StatementParser(
    private val ifParser: Parser<StatementAstNode>,
    private val whileParser: Parser<StatementAstNode>,
    private val forParser: Parser<StatementAstNode>,
    private val variableInitializationParser: Parser<StatementAstNode>,
    private val assignParser: Parser<StatementAstNode>,
    private val callParser: Parser<StatementAstNode>,
    private val returnParser: Parser<ReturnFunctionStatementAstNode>,
) : Parser<StatementAstNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): StatementAstNode =
        when (context.top()) {
            is Token.Eof -> {
                raise(ASTError("Unexpected end of file in ${context.top().position}"))
            }
            is Token.Keyword.Val,
            is Token.Keyword.Var -> parseWith(variableInitializationParser, context)

            is Token.Keyword.Return -> parseWith(returnParser, context)

            is Token.Identifier -> {
                when (context.top(1)) {
                    is Token.Eof -> raise(ASTError("Unexpected end of file in ${context.top(1).position}"))
                    is Token.Punctuation.LParen -> parseWith(callParser, context)
                    else -> parseWith(assignParser, context)
                }
            }

            is Token.Keyword.If -> parseWith(ifParser, context)
            is Token.Keyword.While -> parseWith(whileParser, context)
            is Token.Keyword.For -> parseWith(forParser, context)

            else -> raise(ASTError("Didn't expect ${context.top()} in ${context.top().position}"))
        }
}
