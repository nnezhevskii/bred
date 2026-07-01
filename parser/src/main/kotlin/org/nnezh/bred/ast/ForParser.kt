package org.nnezh.bred.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.bred.ast.AssignmentStatementAstNode
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.AstErrorFactory.buildError
import org.nnezh.bred.ast.BinaryExpressionASTNode
import org.nnezh.bred.ast.BinaryOperator
import org.nnezh.bred.ast.BlockAstNode
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.bred.ast.ForStatementAstNode
import org.nnezh.bred.ast.IntLiteralExpressionNode
import org.nnezh.bred.ast.LocatedBinaryOperator
import org.nnezh.bred.ast.Parser
import org.nnezh.bred.ast.ScalarVariableInitializationASTNode
import org.nnezh.bred.ast.TokensContext
import org.nnezh.bred.ast.VariableExpressionNode
import org.nnezh.bred.ast.WhileStatementAstNode
import org.nnezh.bred.ast.match
import org.nnezh.bred.ast.parseWith
import org.nnezh.bred.common.TypeSign
import org.nnezh.lexer.Token

class ForParser(
    private val expressionParser: Parser<ExpressionASTNode>,
    private val blockParser: Lazy<Parser<BlockAstNode>>,
) : Parser<ForStatementAstNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): ForStatementAstNode {
        match<Token.Keyword.For>(context.consumeToken()) { buildError("for", it) }
        match<Token.Punctuation.LParen>(context.consumeToken()) { buildError("(", it) }
        val counterName = match<Token.Identifier>(context.consumeToken()) { buildError("for-variable", it) }
        match<Token.Keyword.In>(context.consumeToken()) { buildError("in", it) }
        val initialValue = parseWith(expressionParser, context)
        val toKeyword = match<Token.Keyword.To>(context.consumeToken()) { buildError("to", it) }
        val finalValue = parseWith(expressionParser, context)
        match<Token.Punctuation.RParen>(context.consumeToken()) { buildError(")", it) }
        val innerBlockStatements = parseWith(blockParser.value, context).statements
        val syntheticOpPosition = toKeyword.position
        val counterInit = ScalarVariableInitializationASTNode(counterName.lexeme,
            TypeSign("Int"), initialValue, isMutable = true)
        val toVariableName = "_right_border${counterName.lexeme}"
        val rightBorderInit = ScalarVariableInitializationASTNode(toVariableName, TypeSign("Int"), finalValue, isMutable = false)
        val limitToken = Token.Identifier(toVariableName, syntheticOpPosition)
        val counter = VariableExpressionNode(counterName)
        val desugared = listOf(
            counterInit,
            rightBorderInit,
            WhileStatementAstNode(
                BinaryExpressionASTNode(
                    counter,
                    LocatedBinaryOperator(BinaryOperator.Lt, syntheticOpPosition),
                    VariableExpressionNode(limitToken),
                ),
                bodyBlock = BlockAstNode(
                    innerBlockStatements +
                            AssignmentStatementAstNode(
                                counter,
                                BinaryExpressionASTNode(
                                    counter,
                                    LocatedBinaryOperator(BinaryOperator.Plus, syntheticOpPosition),
                                    IntLiteralExpressionNode(1)
                                )
                            )
                ),
            )
        )
        return ForStatementAstNode(BlockAstNode(desugared))
    }
}
