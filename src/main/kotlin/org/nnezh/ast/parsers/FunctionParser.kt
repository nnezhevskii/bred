package org.nnezh.org.nnezh.ast.parsers

import arrow.core.left
import arrow.core.raise.Raise
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.DeclareFunctionASTNode
import org.nnezh.ast.FunctionArgsASTNode
import org.nnezh.ast.FunctionArgumentASTNode
import org.nnezh.ast.ReturnFunctionStatementASTNode
import org.nnezh.ast.StatementASTNode
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.ASTError
import org.nnezh.org.nnezh.ast.AstErrorFactory
import org.nnezh.org.nnezh.ast.TokensContext
import org.nnezh.org.nnezh.ast.match
import org.nnezh.org.nnezh.ast.parseType
import org.nnezh.org.nnezh.base.Type

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

                    arguments.add(FunctionArgumentASTNode(argName.lexeme, parseType(argType)))
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
                resultType = parseType(type)
            }
            is Token.Identifier -> {
                raise(ASTError("Expected ':' before return type but got ${token.lexeme} at ${token.position}"))
            }
            else -> raise(ASTError("Expected '{' or ':' before function body but got ${token.lexeme} at ${token.position}"))
        }
        val block: BlockASTNode = parseWith(blockParser.value, context)
        val finalBlockStatements: List<StatementASTNode> = if (block.statements.none { statement -> statement is ReturnFunctionStatementASTNode } ) {
            block.statements + ReturnFunctionStatementASTNode(Type.UnitType.left())
        } else {
            block.statements
        }

        return DeclareFunctionASTNode(name.lexeme, FunctionArgsASTNode(arguments), BlockASTNode(finalBlockStatements), resultType)
    }
}
