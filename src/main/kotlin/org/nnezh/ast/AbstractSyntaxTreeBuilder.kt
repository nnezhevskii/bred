package org.nnezh.ast

import arrow.core.Either
import arrow.core.raise.either
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.ASTError
import org.nnezh.org.nnezh.ast.AbstractSyntaxTreeExpressionParser
import org.nnezh.org.nnezh.ast.Parser
import org.nnezh.org.nnezh.ast.ParserFactory
import org.nnezh.org.nnezh.ast.TokensContext
import org.nnezh.org.nnezh.ast.parseWith

class AbstractSyntaxTreeBuilder(
    expressionParser: Parser<ExpressionASTNode> = AbstractSyntaxTreeExpressionParser(),
    factory: ParserFactory = ParserFactory(expressionParser = expressionParser),
) {
    private val programParser = factory.programParser()

    fun build(tokens: List<Token>): Either<ASTError, ASTNode> = either {
        parseWith(programParser, TokensContext(tokens))
    }
}
