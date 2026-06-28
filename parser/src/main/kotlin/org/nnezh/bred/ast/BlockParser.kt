package org.nnezh.bred.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.AstErrorFactory
import org.nnezh.bred.ast.BlockAstNode
import org.nnezh.bred.ast.Parser
import org.nnezh.bred.ast.StatementAstNode
import org.nnezh.bred.ast.TokensContext
import org.nnezh.bred.ast.match
import org.nnezh.bred.ast.parseWith
import org.nnezh.lexer.Token

class BlockParser(
    private val statementParser: Lazy<Parser<StatementAstNode>>,
) : Parser<BlockAstNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): BlockAstNode {
        val statements = mutableListOf<StatementAstNode>()
        match<Token.Punctuation.LBrace>(context.consumeToken()) {
            ASTError("Expected begin of block but got ${it.lexeme} in ${it.position}")
        }

        while (true) {
            if (context.endOfInput) {
                raise(AstErrorFactory.unexpectedEOF(context.top()))
            }
            if (context.top() is Token.Punctuation.RBrace) {
                break
            }
            statements.add(parseWith(statementParser.value, context))
        }
        match<Token.Punctuation.RBrace>(context.consumeToken()) {
            ASTError("Expected end of block but got ${it.lexeme} in ${it.position}")
        }

        return BlockAstNode(statements)
    }
}
