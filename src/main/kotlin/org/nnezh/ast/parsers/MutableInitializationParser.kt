package org.nnezh.org.nnezh.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.MutableVariableInitializationASTNode
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.ASTError
import org.nnezh.org.nnezh.ast.AstErrorFactory.buildError
import org.nnezh.org.nnezh.ast.TokensContext
import org.nnezh.org.nnezh.ast.match
import org.nnezh.org.nnezh.ast.parseType

class MutableInitializationParser(
    private val expressionParser: Parser<ExpressionASTNode>,
) : Parser<MutableVariableInitializationASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): MutableVariableInitializationASTNode {
        match<Token.Keyword.Var>(context.consumeToken()) { token -> ASTError("Expected var but got ${token.lexeme} in ${token.position}") }
        val varName = match<Token.Identifier>(context.consumeToken()) { token -> buildError("variable name", token) }
        match<Token.Punctuation.Colon>(context.consumeToken()) { token -> buildError(":", token) }
        val type = match<Token.Identifier>(context.consumeToken()) { token -> buildError("variable type", token) }

        match<Token.Operator.Assign>(context.consumeToken()) { token -> buildError("assignation", token) }

        val value: ExpressionASTNode = parseWith(expressionParser, context)

        return MutableVariableInitializationASTNode(varName.lexeme, parseType(type), value)
    }
}