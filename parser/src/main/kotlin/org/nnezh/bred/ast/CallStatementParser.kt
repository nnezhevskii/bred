package org.nnezh.bred.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.CallFunctionStatementAstNode
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.bred.ast.Parser
import org.nnezh.bred.ast.TokensContext
import org.nnezh.bred.ast.parseWith

class CallStatementParser(
    private val expressionParser: Parser<ExpressionASTNode>,
) : Parser<CallFunctionStatementAstNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): CallFunctionStatementAstNode =
        CallFunctionStatementAstNode(parseWith(expressionParser, context))
}
