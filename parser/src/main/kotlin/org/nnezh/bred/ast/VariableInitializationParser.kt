package org.nnezh.bred.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.AstErrorFactory.buildError
import org.nnezh.bred.ast.ArrayDeclarationASTNode
import org.nnezh.bred.ast.DeclareVariableASTNode
import org.nnezh.bred.ast.ExpressionASTNode
import org.nnezh.bred.ast.Parser
import org.nnezh.bred.ast.ScalarVariableInitializationASTNode
import org.nnezh.bred.ast.StaticArrayInitializationExpressionsListNode
import org.nnezh.bred.ast.TokensContext
import org.nnezh.bred.ast.match
import org.nnezh.bred.ast.parseWith
import org.nnezh.bred.common.TypeSign
import org.nnezh.lexer.Token

class VariableInitializationParser(
    private val expressionParser: Parser<ExpressionASTNode>,
    private val typeSignParser: TypeSignParser = TypeSignParser(),
) : Parser<DeclareVariableASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): DeclareVariableASTNode {
        val isMutable = when (val token = context.consumeToken()) {
            is Token.Keyword.Val -> false
            is Token.Keyword.Var -> true
            else -> raise(buildError("val or var", token))
        }
        val name = match<Token.Identifier>(context.consumeToken()) { token -> buildError("variable name", token) }
        match<Token.Punctuation.Colon>(context.consumeToken()) { token -> buildError(":", token) }
        val type = with(typeSignParser) { parse(context) }

        if (!isMutable && context.top() is Token.Punctuation.LBracket) {
            return parseImmutableArray(name.lexeme, type, context)
        }

        match<Token.Operator.Assign>(context.consumeToken()) { token -> buildError("assignation", token) }
        val value = parseWith(expressionParser, context)
        return ScalarVariableInitializationASTNode(name.lexeme, type, value, isMutable)
    }

    private fun Raise<ASTError>.parseImmutableArray(
        name: String,
        type: TypeSign,
        context: TokensContext,
    ): ArrayDeclarationASTNode {
        context.consumeToken()
        val size = match<Token.Literal.IntLiteral>(context.consumeToken()) { token -> buildError("array size", token) }
        match<Token.Punctuation.RBracket>(context.consumeToken()) { token -> buildError("]", token) }

        val value = if (context.top() is Token.Operator.Assign) {
            context.consumeToken()
            val parsed = parseWith(expressionParser, context)
            parsed as? StaticArrayInitializationExpressionsListNode
                ?: raise(buildError("array initialization list", context.top()))
        } else {
            null
        }

        return ArrayDeclarationASTNode(
            name = name,
            type = type,
            size = size.value.toInt(),
            expression = value,
            isMutable = false,
        )
    }
}
