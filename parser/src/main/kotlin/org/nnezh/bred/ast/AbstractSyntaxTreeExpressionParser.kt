package org.nnezh.bred.ast

import arrow.core.raise.Raise
import org.nnezh.bred.ast.parsers.TypeSignParser
import org.nnezh.bred.common.TypeSign
import org.nnezh.lexer.Token

/**
 * Recursive-descent parser for bred expressions.
 *
 * Handles operator precedence, parentheses and function calls with any number
 * of arguments. Errors are reported through the [arrow.core.raise.Raise] DSL ([org.nnezh.bred.ast.ASTError]) rather
 * than thrown, matching the rest of the AST builder.
 *
 * Precedence, from lowest to highest:
 * `||`, `&&`, `== !=`, `< > <= >=`, `+ -`, `* / %`, unary `- !`, primary.
 * All binary operators are left-associative; unary operators are right-associative.
 */
class AbstractSyntaxTreeExpressionParser(
    private val typeSignParser: TypeSignParser = TypeSignParser(),
) : Parser<ExpressionASTNode> {

    override fun Raise<ASTError>.parse(context: TokensContext): ExpressionASTNode = parseLogicalOr(context)

    private fun Raise<ASTError>.parseLogicalOr(context: TokensContext): ExpressionASTNode {
        var left = parseLogicalAnd(context)
        while (context.top() is Token.Operator.Or) {
            val op = context.consumeToken() as Token.Operator
            val right = parseLogicalAnd(context)
            left = BinaryExpressionASTNode(left, op.toLocatedBinaryOperator(), right)
        }
        return left
    }

    private fun Raise<ASTError>.parseLogicalAnd(context: TokensContext): ExpressionASTNode {
        var left = parseEquality(context)
        while (context.top() is Token.Operator.And) {
            val op = context.consumeToken() as Token.Operator
            val right = parseEquality(context)
            left = BinaryExpressionASTNode(left, op.toLocatedBinaryOperator(), right)
        }
        return left
    }

    private fun Raise<ASTError>.parseEquality(context: TokensContext): ExpressionASTNode {
        var left = parseComparison(context)
        while (context.top().let { it is Token.Operator.Eq || it is Token.Operator.Neq }) {
            val op = context.consumeToken() as Token.Operator
            val right = parseComparison(context)
            left = BinaryExpressionASTNode(left, op.toLocatedBinaryOperator(), right)
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
            val op = context.consumeToken() as Token.Operator
            val right = parseAdditive(context)
            left = BinaryExpressionASTNode(left, op.toLocatedBinaryOperator(), right)
        }
        return left
    }

    private fun Raise<ASTError>.parseAdditive(context: TokensContext): ExpressionASTNode {
        var left = parseMultiplicative(context)
        while (context.top().let { it is Token.Operator.Plus || it is Token.Operator.Minus }) {
            val op = context.consumeToken() as Token.Operator
            val right = parseMultiplicative(context)
            left = BinaryExpressionASTNode(left, op.toLocatedBinaryOperator(), right)
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
            val op = context.consumeToken() as Token.Operator
            val right = parseUnary(context)
            left = BinaryExpressionASTNode(left, op.toLocatedBinaryOperator(), right)
        }
        return left
    }

    private fun Raise<ASTError>.parseUnary(context: TokensContext): ExpressionASTNode {
        val token = context.top()
        return if (token is Token.Operator.Minus || token is Token.Operator.Not) {
            val op = context.consumeToken() as Token.Operator
            UnaryExpressionASTNode(op.toLocatedUnaryOperator(), parseUnary(context))
        } else {
            parsePrimary(context)
        }
    }

    private fun Raise<ASTError>.parsePrimary(context: TokensContext): ExpressionASTNode =
        when (val token = context.top()) {
            is Token.Literal.IntLiteral -> {
                context.consumeToken()
                IntLiteralExpressionASTNode(token.value.toInt())
            }

            is Token.Literal.LongLiteral -> {
                context.consumeToken()
                LongLiteralExpressionASTNode(token.value)
            }

            is Token.Literal.DoubleLiteral -> {
                context.consumeToken()
                DoubleLiteralExpressionASTNode(token.value)
            }

            is Token.Literal.StringLiteral -> {
                context.consumeToken()
                StringLiteralExpressionASTNode(token.value)
            }

            is Token.Keyword.True -> {
                context.consumeToken()
                BooleanLiteralExpressionASTNode(true)
            }

            is Token.Keyword.False -> {
                context.consumeToken()
                BooleanLiteralExpressionASTNode(false)
            }

            is Token.Identifier -> {
                context.consumeToken()
                val node = when {
                    context.top() is Token.Punctuation.LParen -> parseCall(token, context)
                    context.top() is Token.Operator.Lt && isGenericCallSuffix(context) -> {
                        val typeArguments = parseCallTypeArguments(context)
                        parseCall(token, context, typeArguments)
                    }
                    context.top() is Token.Punctuation.LBracket -> parseArrayAccess(token, context)
                    context.top() is Token.Punctuation.Dot -> parseFieldAccessChain(token, context)
                    else -> VariableExpressionASTNode(token)
                }
                if (node is ArrayElementAccessASTNode && context.top() is Token.Punctuation.LParen) {
                    raise(AstErrorFactory.buildError("expression after array access", context.top()))
                }
                node
            }

            is Token.Punctuation.LParen -> {
                context.consumeToken()
                val inner = parse(context)
                match<Token.Punctuation.RParen>(context.consumeToken()) { AstErrorFactory.expectedClosingParen(it) }
                inner
            }

            is Token.Punctuation.LBracket -> {
                context.consumeToken()
                val arguments = mutableListOf<ExpressionASTNode>()
                if (context.top() !is Token.Punctuation.RBracket) {
                    while (true) {
                        arguments.add(parse(context))
                        when (context.top()) {
                            is Token.Punctuation.Comma -> {
                                context.consumeToken()
                                if (context.top() is Token.Punctuation.RBracket || context.top() is Token.Punctuation.Comma) {
                                    raise(AstErrorFactory.expectedExpression(context.top()))
                                }
                            }
                            is Token.Punctuation.RBracket -> break
                            else -> raise(AstErrorFactory.buildError("',' or ']'", context.top()))
                        }
                    }
                }

                match<Token.Punctuation.RBracket>(context.consumeToken()) { AstErrorFactory.buildError("]", it) }
                ArrayInitializationExpressionASTNode(arguments)
            }

            else -> raise(AstErrorFactory.expectedExpression(token))
        }

    private fun Raise<ASTError>.parseFieldAccessChain(token: Token, context: TokensContext): FieldAccessChainExpressionASTNode {
        val rootName = token.lexeme
        var next: FieldAccessChainExpressionASTNode? = null

        if (context.top() is Token.Punctuation.Dot) {
            context.consumeToken()
            val chain = match<Token.Identifier>(context.consumeToken()) { AstErrorFactory.buildError("identifier", it) }
            next = parseFieldAccessChain(chain, context)
        }


        return FieldAccessChainExpressionASTNode(rootName, next)
    }

    private fun Raise<ASTError>.parseArrayAccess(name: Token, context: TokensContext): ArrayElementAccessASTNode {
        context.consumeToken() // '['
        val index = parse(context)
        match<Token.Punctuation.RBracket>(context.consumeToken()) { AstErrorFactory.buildError("]", it) }
        return ArrayElementAccessASTNode(name.lexeme, index)
    }

    private fun isGenericCallSuffix(context: TokensContext): Boolean {
        var offset = 0
        var depth = 0
        while (true) {
            when (context.top(offset)) {
                is Token.Operator.Lt -> depth++
                is Token.Operator.Gt -> {
                    depth--
                    if (depth == 0) {
                        return context.top(offset + 1) is Token.Punctuation.LParen
                    }
                    if (depth < 0) {
                        return false
                    }
                }
                is Token.Eof -> return false
                else -> Unit
            }
            offset++
        }
    }

    private fun Raise<ASTError>.parseCallTypeArguments(context: TokensContext): List<TypeSign> {
        match<Token.Operator.Lt>(context.consumeToken()) { AstErrorFactory.buildError("<", it) }
        val typeArguments = mutableListOf<TypeSign>()
        if (context.top() is Token.Operator.Gt) {
            raise(AstErrorFactory.buildError("type argument", context.top()))
        }
        while (true) {
            typeArguments += with(typeSignParser) { parse(context) }
            when (context.top()) {
                is Token.Punctuation.Comma -> context.consumeToken()
                is Token.Operator.Gt -> {
                    context.consumeToken()
                    break
                }
                else -> raise(AstErrorFactory.buildError("',' or '>'", context.top()))
            }
        }
        return typeArguments
    }

    private fun Raise<ASTError>.parseCall(
        name: Token,
        context: TokensContext,
        typeArguments: List<TypeSign> = emptyList(),
    ): FunctionCallExpressionASTNode {
        context.consumeToken() // '('
        val arguments = mutableListOf<ExpressionASTNode>()
        if (context.top() is Token.Punctuation.RParen) {
            context.consumeToken()
            return FunctionCallExpressionASTNode(name.lexeme, arguments, typeArguments)
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
        return FunctionCallExpressionASTNode(name.lexeme, arguments, typeArguments)
    }
}
