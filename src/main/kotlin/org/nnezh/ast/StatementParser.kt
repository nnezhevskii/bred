package org.nnezh.org.nnezh.ast

import arrow.core.raise.Raise
import org.nnezh.ast.FunctionArgumentASTNode
import org.nnezh.ast.MutableVariableInitializationASTNode
import org.nnezh.ast.ReturnFunctionStatementASTNode
import org.nnezh.ast.StatementASTNode
import org.nnezh.lexer.Token

class StatementParser(
    private val ifParser: Parser<StatementASTNode>,
    private val whileParser: Parser<StatementASTNode>,
    private val forParser: Parser<StatementASTNode>,
    private val immutableInitializationParser: Parser<StatementASTNode>,
    private val mutableInitializationParser: Parser<StatementASTNode>,
    private val assignParser: Parser<StatementASTNode>,
    private val callParser: Parser<StatementASTNode>,
    private val returnParser: Parser<ReturnFunctionStatementASTNode>,
) : Parser<StatementASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): StatementASTNode =
        when (context.top()) {
            is Token.Eof -> {
                raise(ASTError("Unexpected end of file in ${context.top().position}"))
            }
            is Token.Keyword.Val -> parseWith(immutableInitializationParser, context)
            is Token.Keyword.Var -> parseWith(mutableInitializationParser, context)

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
