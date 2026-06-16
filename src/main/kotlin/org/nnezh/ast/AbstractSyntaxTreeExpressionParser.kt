package org.nnezh.org.nnezh.ast

import arrow.core.raise.Raise
import org.nnezh.ast.BinaryExpressionASTNode
import org.nnezh.ast.BooleanLiteralExpressionNode
import org.nnezh.ast.DoubleLiteralExpressionNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.FunctionCallExpressionNode
import org.nnezh.ast.IntLiteralExpressionNode
import org.nnezh.ast.StringLiteralExpressionNode
import org.nnezh.ast.UnaryExpressionASTNode
import org.nnezh.ast.VariableExpressionNode
import org.nnezh.lexer.Token

/* The expression parser is one concrete [Parser] implementation among the
 * statement parsers wired together by [ParserFactory]. */

/**
 * Recursive-descent parser for bred expressions.
 *
 * Handles operator precedence, parentheses and function calls with any number
 * of arguments. Errors are reported through the [Raise] DSL ([ASTError]) rather
 * than thrown, matching the rest of the AST builder.
 *
 * Precedence, from lowest to highest:
 * `||`, `&&`, `== !=`, `< > <= >=`, `+ -`, `* / %`, unary `- !`, primary.
 * All binary operators are left-associative; unary operators are right-associative.
 */
class AbstractSyntaxTreeExpressionParser : Parser<ExpressionASTNode> {

    override fun Raise<ASTError>.parse(context: TokensContext): ExpressionASTNode = parseLogicalOr(context)

    private fun Raise<ASTError>.parseLogicalOr(context: TokensContext): ExpressionASTNode {
        var left = parseLogicalAnd(context)
        while (context.top() is Token.Operator.Or) {
            val op = context.consumeToken()
            val right = parseLogicalAnd(context)
            left = BinaryExpressionASTNode(left, op, right)
        }
        return left
    }

    private fun Raise<ASTError>.parseLogicalAnd(context: TokensContext): ExpressionASTNode {
        var left = parseEquality(context)
        while (context.top() is Token.Operator.And) {
            val op = context.consumeToken()
            val right = parseEquality(context)
            left = BinaryExpressionASTNode(left, op, right)
        }
        return left
    }

    private fun Raise<ASTError>.parseEquality(context: TokensContext): ExpressionASTNode {
        var left = parseComparison(context)
        while (context.top().let { it is Token.Operator.Eq || it is Token.Operator.Neq }) {
            val op = context.consumeToken()
            val right = parseComparison(context)
            left = BinaryExpressionASTNode(left, op, right)
        }
        return left
    }

    private fun Raise<ASTError>.parseComparison(context: TokensContext): ExpressionASTNode {
        var left = parseAdditive(context)
        while (
            context.top().let {
                it is Token.Operator.Lt || it is Token.Operator.Gt ||
                    it is Token.Operator.Le || it is Token.Operator.Ge
            }
        ) {
            val op = context.consumeToken()
            val right = parseAdditive(context)
            left = BinaryExpressionASTNode(left, op, right)
        }
        return left
    }

    private fun Raise<ASTError>.parseAdditive(context: TokensContext): ExpressionASTNode {
        var left = parseMultiplicative(context)
        while (context.top().let { it is Token.Operator.Plus || it is Token.Operator.Minus }) {
            val op = context.consumeToken()
            val right = parseMultiplicative(context)
            left = BinaryExpressionASTNode(left, op, right)
        }
        return left
    }

    private fun Raise<ASTError>.parseMultiplicative(context: TokensContext): ExpressionASTNode {
        var left = parseUnary(context)
        while (
            context.top().let {
                it is Token.Operator.Star || it is Token.Operator.Slash || it is Token.Operator.Percent
            }
        ) {
            val op = context.consumeToken()
            val right = parseUnary(context)
            left = BinaryExpressionASTNode(left, op, right)
        }
        return left
    }

    private fun Raise<ASTError>.parseUnary(context: TokensContext): ExpressionASTNode {
        val token = context.top()
        return if (token is Token.Operator.Minus || token is Token.Operator.Not) {
            val op = context.consumeToken()
            UnaryExpressionASTNode(op, parseUnary(context))
        } else {
            parsePrimary(context)
        }
    }

    private fun Raise<ASTError>.parsePrimary(context: TokensContext): ExpressionASTNode =
        when (val token = context.top()) {
            is Token.Literal.IntLiteral -> {
                context.consumeToken()
                IntLiteralExpressionNode(token.value)
            }

            is Token.Literal.DoubleLiteral -> {
                context.consumeToken()
                DoubleLiteralExpressionNode(token.value)
            }

            is Token.Literal.StringLiteral -> {
                context.consumeToken()
                StringLiteralExpressionNode(token.value)
            }

            is Token.Keyword.True -> {
                context.consumeToken()
                BooleanLiteralExpressionNode(true)
            }

            is Token.Keyword.False -> {
                context.consumeToken()
                BooleanLiteralExpressionNode(false)
            }

            is Token.Identifier -> {
                context.consumeToken()
                if (context.top() is Token.Punctuation.LParen) {
                    parseCall(token, context)
                } else {
                    VariableExpressionNode(token)
                }
            }

            is Token.Punctuation.LParen -> {
                context.consumeToken()
                val inner = parse(context)
                match<Token.Punctuation.RParen>(context.consumeToken()) { AstErrorFactory.expectedClosingParen(it) }
                inner
            }

            else -> raise(AstErrorFactory.expectedExpression(token))
        }

    private fun Raise<ASTError>.parseCall(name: Token, context: TokensContext): FunctionCallExpressionNode {
        context.consumeToken() // '('
        val arguments = mutableListOf<ExpressionASTNode>()
        if (context.top() is Token.Punctuation.RParen) {
            context.consumeToken()
            return FunctionCallExpressionNode(name, arguments)
        }
        while (true) {
            arguments += parse(context)
            when (context.top()) {
                is Token.Punctuation.Comma -> context.consumeToken()
                is Token.Punctuation.RParen -> {
                    context.consumeToken()
                    break
                }
                else -> raise(AstErrorFactory.expectedArgumentSeparator(context.top()))
            }
        }
        return FunctionCallExpressionNode(name, arguments)
    }
}
