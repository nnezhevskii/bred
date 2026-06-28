package org.nnezh.bred.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.AstErrorFactory
import org.nnezh.bred.ast.BlockAstNode
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.Parser
import org.nnezh.bred.ast.ReturnFunctionStatementAstNode
import org.nnezh.bred.ast.StatementAstNode
import org.nnezh.bred.ast.TokensContext
import org.nnezh.bred.ast.match
import org.nnezh.bred.ast.parseWith
import org.nnezh.lexer.Token

class FunctionParser(
    private val blockParser: Lazy<Parser<BlockAstNode>>,
    private val typeSignParser: TypeSignParser = TypeSignParser(),
) : Parser<FunctionDeclAstNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): FunctionDeclAstNode {
        match<Token.Keyword.Fun>(context.consumeToken()) { token -> ASTError("Expected fun but got ${token.lexeme}") }
        val name =
            match<Token.Identifier>(context.consumeToken()) { token -> ASTError("Expected fun name but got ${token.lexeme}") }
        val genericParams = with(typeSignParser) { parseGenericParams(context) }
        val arguments = with(typeSignParser) { parseArguments(context) }

        val resultType = when (val token = context.top()) {
            is Token.Punctuation.LBrace -> org.nnezh.bred.ast.TypeSign("Unit")
            is Token.Punctuation.Colon -> {
                context.consumeToken()
                with(typeSignParser) { parse(context) }
            }
            is Token.Identifier -> {
                raise(ASTError("Expected ':' before return type but got ${token.lexeme} at ${token.position}"))
            }
            else -> raise(ASTError("Expected '{' or ':' before function body but got ${token.lexeme} at ${token.position}"))
        }
        val block: BlockAstNode = parseWith(blockParser.value, context)
        val finalBlockStatements: List<StatementAstNode> = if (block.statements.none { statement -> statement is ReturnFunctionStatementAstNode } ) {
            block.statements + ReturnFunctionStatementAstNode(null, false)
        } else {
            block.statements
        }

        return FunctionDeclAstNode(name.lexeme, genericParams, arguments, resultType, BlockAstNode(finalBlockStatements))
    }
}
