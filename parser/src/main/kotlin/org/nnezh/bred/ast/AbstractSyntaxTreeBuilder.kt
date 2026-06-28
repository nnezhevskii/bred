package org.nnezh.bred.ast

import arrow.core.Either
import arrow.core.raise.either
import org.nnezh.lexer.Token

class AbstractSyntaxTreeBuilder(
    expressionParser: Parser<ExpressionASTNode> = AbstractSyntaxTreeExpressionParser(),
    factory: ParserFactory = ParserFactory(expressionParser = expressionParser),
) {
    private val programParser = factory.programParser()

    fun build(tokens: List<Token>): Either<ASTError, ASTNode> = either {
        parseWith(programParser, TokensContext(tokens))
    }
}
