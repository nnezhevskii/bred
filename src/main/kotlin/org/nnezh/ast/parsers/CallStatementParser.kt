package org.nnezh.org.nnezh.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.ast.CallFunctionStatementASTNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.org.nnezh.ast.ASTError
import org.nnezh.org.nnezh.ast.TokensContext

class CallStatementParser(
    private val expressionParser: Parser<ExpressionASTNode>,
) : Parser<CallFunctionStatementASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): CallFunctionStatementASTNode =
        CallFunctionStatementASTNode(parseWith(expressionParser, context))
}
