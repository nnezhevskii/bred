package org.nnezh.org.nnezh.ast

import arrow.core.raise.Raise
import org.nnezh.ast.AssignmentStatementASTNode
import org.nnezh.ast.BinaryExpressionASTNode
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.ForStatementASTNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.MutableVariableInitializationASTNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.ast.WhileStatementASTNode
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.Type
import org.nnezh.org.nnezh.ast.AstErrorFactory.buildError

class ForParser(
    private val expressionParser: Parser<ExpressionASTNode>,
    private val blockParser: Lazy<Parser<BlockASTNode>>,
) : Parser<ForStatementASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): ForStatementASTNode {
        match<Token.Keyword.For>(context.consumeToken()) { buildError("for", it) }
        match<Token.Punctuation.LParen>(context.consumeToken()) { buildError("(", it) }
        val counterName = match<Token.Identifier>(context.consumeToken()) { buildError("for-variable", it) }
        match<Token.Keyword.In>(context.consumeToken()) { buildError("in", it) }
        val initialValue = parseWith(expressionParser, context)
        match<Token.Keyword.To>(context.consumeToken()) { buildError("to", it) }
        val finalValue = parseWith(expressionParser, context)
        match<Token.Punctuation.RParen>(context.consumeToken()) { buildError(")", it) }
        val innerBlockStatements = parseWith(blockParser.value, context).statements
        val counter = MutableVariableInitializationASTNode(counterName.lexeme, Type.IntType, initialValue)
        val desugared = listOf(
            counter,
            WhileStatementASTNode(
                BinaryExpressionASTNode(
                    VariableExpressionNode(counterName),
                    Token.Operator.Le(context.top().position), // TODO << doesn't seem right. Recalculate? or change arch?
                    finalValue,
                ),
                bodyBlock = BlockASTNode(
                    innerBlockStatements +
                            AssignmentStatementASTNode(
                                counterName.lexeme,
                                BinaryExpressionASTNode(
                                    VariableExpressionNode(counterName),
                                    Token.Operator.Plus(context.top().position),
                                    IntLiteralExpressionNode(1)
                                )
                            )
                ),
            )
        )
        return ForStatementASTNode(BlockASTNode(desugared))
    }
}
