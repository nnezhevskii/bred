package org.nnezh.org.nnezh.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.ImmutableVariableInitializationASTNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.StaticArrayExpressionNode
import org.nnezh.ast.StaticArrayInitializationExpressionsListNode
import org.nnezh.ast.VariableInitializationASTNode
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.ASTError
import org.nnezh.org.nnezh.ast.AstErrorFactory.buildError
import org.nnezh.org.nnezh.ast.TokensContext
import org.nnezh.org.nnezh.ast.match
import org.nnezh.org.nnezh.ast.parseType

// TODO: merge ImmutableInitializationParser and MutableInitializationParser
class ImmutableInitializationParser(
    private val expressionParser: Parser<ExpressionASTNode>,
) : Parser<VariableInitializationASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): VariableInitializationASTNode {
        match<Token.Keyword.Val>(context.consumeToken()) { token -> ASTError("Expected val but got ${token.lexeme} in ${token.position}") }
        val valName = match<Token.Identifier>(context.consumeToken()) { token -> buildError("variable name", token) }
        match<Token.Punctuation.Colon>(context.consumeToken()) { token -> buildError(":", token) }
        val type = match<Token.Identifier>(context.consumeToken()) { token -> buildError("variable type", token) }

        if (context.top() is Token.Punctuation.LBracket) {
            context.consumeToken()
            val size = match<Token.Literal.IntLiteral>(context.consumeToken()) { token -> buildError("array size", token) }
            match<Token.Punctuation.RBracket>(context.consumeToken()) { token -> buildError("]", token) }
            if (context.top() is Token.Operator.Assign) {
                match<Token.Operator.Assign>(context.consumeToken()) { token -> buildError("assignation", token) }
                val value: StaticArrayInitializationExpressionsListNode = parseWith(expressionParser, context) as StaticArrayInitializationExpressionsListNode

                return StaticArrayExpressionNode(
                    variableName = valName.lexeme,
                    variableType = parseType(type),
                    size = size.value.toInt(),
                    isMutable = true, // TOOO: не совсем правда
                    value)
            } else {
                return StaticArrayExpressionNode(
                    variableName = valName.lexeme,
                    variableType = parseType(type),
                    size = size.value.toInt(),
                    isMutable = true, // TOOO: не совсем правда
                    valExpression = null)
            }

        } else {
            match<Token.Operator.Assign>(context.consumeToken()) { token -> buildError("assignation", token) }
            val value: ExpressionASTNode = parseWith(expressionParser, context)
            return ImmutableVariableInitializationASTNode(valName.lexeme, parseType(type), value)
        }
    }
}
