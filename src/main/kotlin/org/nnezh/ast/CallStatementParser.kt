package org.nnezh.org.nnezh.ast

import arrow.core.raise.Raise
import org.nnezh.ast.CallFunctionStatementASTNode
import org.nnezh.ast.ExpressionASTNode

class CallStatementParser(
    private val expressionParser: Parser<ExpressionASTNode>,
) : Parser<CallFunctionStatementASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): CallFunctionStatementASTNode =
        CallFunctionStatementASTNode(parseWith(expressionParser, context))
}
