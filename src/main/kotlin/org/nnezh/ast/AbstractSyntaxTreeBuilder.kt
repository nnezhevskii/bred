package org.nnezh.ast

import arrow.core.Either
import arrow.core.raise.either
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.ASTError
import arrow.core.raise.Raise
import arrow.core.raise.ensure
import org.nnezh.org.nnezh.ast.AstErrorFactory
import org.nnezh.org.nnezh.ast.TokensContext
import org.nnezh.org.nnezh.ast.match

class AbstractSyntaxTreeBuilder {
    fun build(tokens: List<Token>): Either<ASTError, ASTNode> = either {
        val context = TokensContext(tokens)

        parseProgram(context)
    }

    private fun Raise<ASTError>.parseProgram(context: TokensContext): ProgramASTNode {
        val functions = mutableListOf<DeclareFunctionASTNode>()
        val globalVariables = mutableListOf<ImmutableVariableInitializationASTNode>()

        while (true) {
            if (context.endOfInput) {
                break
            }

            when (context.top()) {
                is Token.Keyword.Fun -> functions.add(parseFunction(context))
                is Token.Keyword.Val -> globalVariables.add(parseGlobalVariable(context))
                else -> raise(AstErrorFactory.expectedFunOrVariableDeclarationError(context.top()))
            }
        }

        return ProgramASTNode(functions, globalVariables)
    }

    private fun Raise<ASTError>.parseFunction(context: TokensContext): DeclareFunctionASTNode {
        match<Token.Keyword.Fun>(context.consumeToken()) { token -> ASTError("Expected fun but got ${token.lexeme}") }
        val name = match<Token.Identifier>(context.consumeToken())  { token -> ASTError("Expected fun name but got ${token.lexeme}") }
        match<Token.Punctuation.LParen>(context.consumeToken()) { token -> ASTError("Expected ( but got ${token.lexeme}") }

        val arguments = mutableListOf<FunctionArgumentASTNode>()
        var argumentWasJustParsed: Boolean = false
        while (true) { // read arguments
            if (context.endOfInput) {
                raise(AstErrorFactory.unexpectedEOF(context.top()))
            }

            when (context.top()) {

                is Token.Punctuation.Comma -> {
                    if (argumentWasJustParsed) {
                        argumentWasJustParsed = false
                        context.consumeToken()
                        match<Token.Identifier>(context.top()) { token -> ASTError("Expected next argument but got ${token.lexeme} at ${token.position}") }
                    } else {
                        raise(ASTError("Didn't expect comma in ${context.top().position}"))
                    }
                }

                is Token.Identifier -> {
                    val name = match<Token.Identifier>(context.consumeToken()) { token -> ASTError("Expected argument name but got ${token.lexeme} in ${token.position}") }
                    match<Token.Punctuation.Colon>(context.consumeToken()) { token -> ASTError("Expected colon but got ${token.lexeme} in ${token.position}") }
                    val type = match<Token.Identifier>(context.consumeToken()) { token -> ASTError("Expected argument type but got ${token.lexeme} in ${token.position}") }

                    arguments.add(FunctionArgumentASTNode(name.lexeme, type.lexeme))
                    argumentWasJustParsed = true

                }
                is Token.Punctuation.RParen -> {
                    context.consumeToken()
                    break
                }
                else -> raise(ASTError("Failed to build arguments in ${context.top().position} got ${context.top().lexeme}"))
            }
        }
        match<Token.Punctuation.Colon>(context.consumeToken()) { token -> ASTError("Expected colon but got ${token.lexeme} in ${token.position}") }
        val type = match<Token.Identifier>(context.consumeToken())  { token -> ASTError("Expected return type but got ${token.lexeme} in ${token.position}") }
        val block = parseBlockNode(context)

        return DeclareFunctionASTNode(name.lexeme, FunctionArgsASTNode(arguments), block, type.lexeme)
    }

    private fun Raise<ASTError>.parseBlockNode(context: TokensContext): BlockASTNode {
        TODO()
    }

    private fun Raise<ASTError>.parseStatement(context: TokensContext): StatementASTNode {
        TODO()
    }

    private fun Raise<ASTError>.parseGlobalVariable(context: TokensContext): ImmutableVariableInitializationASTNode {
        TODO()
    }

}