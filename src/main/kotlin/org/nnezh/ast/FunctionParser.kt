package org.nnezh.org.nnezh.ast

import arrow.core.raise.Raise
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.DeclareFunctionASTNode
import org.nnezh.ast.FunctionArgsASTNode
import org.nnezh.ast.FunctionArgumentASTNode
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.Type

class FunctionParser(
    private val blockParser: Lazy<Parser<BlockASTNode>>,
) : Parser<DeclareFunctionASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): DeclareFunctionASTNode {
        match<Token.Keyword.Fun>(context.consumeToken()) { token -> ASTError("Expected fun but got ${token.lexeme}") }
        val name =
            match<Token.Identifier>(context.consumeToken()) { token -> ASTError("Expected fun name but got ${token.lexeme}") }
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
                    val argName =
                        match<Token.Identifier>(context.consumeToken()) { token -> ASTError("Expected argument name but got ${token.lexeme} in ${token.position}") }
                    match<Token.Punctuation.Colon>(context.consumeToken()) { token -> ASTError("Expected colon but got ${token.lexeme} in ${token.position}") }
                    val argType =
                        match<Token.Identifier>(context.consumeToken()) { token -> ASTError("Expected argument type but got ${token.lexeme} in ${token.position}") }

                    val resolvedArgType = Type.parseOrNull(argType.lexeme)
                        ?: raise(ASTError("Invalid type ${argType.lexeme} at ${argType.position}"))

                    arguments.add(FunctionArgumentASTNode(argName.lexeme, resolvedArgType))
                    argumentWasJustParsed = true
                }

                is Token.Punctuation.RParen -> {
                    context.consumeToken()
                    break
                }

                else -> raise(ASTError("Failed to build arguments in ${context.top().position} got ${context.top().lexeme}"))
            }
        }

        val resultType: Type
        when (val token = context.top()) {
            is Token.Punctuation.LBrace -> {
                resultType = Type.UnitType
            }
            is Token.Punctuation.Colon -> {
                context.consumeToken()
                val type = match<Token.Identifier>(context.consumeToken()) { token -> ASTError("Expected return type but got ${token.lexeme} in ${token.position}") }
                val typeParsing = Type.fromString(type.lexeme)
                resultType = typeParsing.fold(
                    ifLeft = { _ -> raise(ASTError("Unexpected type ${type.lexeme}")) },
                    ifRight = { rightValue -> rightValue })
            }
            is Token.Identifier -> {
                raise(ASTError("Expected ':' before return type but got ${token.lexeme} at ${token.position}"))
            }
            else -> raise(ASTError("Expected '{' or ':' before function body but got ${token.lexeme} at ${token.position}"))
        }
        val block = parseWith(blockParser.value, context)

        return DeclareFunctionASTNode(name.lexeme, FunctionArgsASTNode(arguments), block, resultType)
    }
}
