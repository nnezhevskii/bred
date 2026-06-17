package org.nnezh.org.nnezh.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.StatementASTNode
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.ASTError
import org.nnezh.org.nnezh.ast.AstErrorFactory
import org.nnezh.org.nnezh.ast.TokensContext
import org.nnezh.org.nnezh.ast.match

class BlockParser(
    private val statementParser: Lazy<Parser<StatementASTNode>>,
) : Parser<BlockASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): BlockASTNode {
        val statements = mutableListOf<StatementASTNode>()
        match<Token.Punctuation.LBrace>(context.consumeToken()) { ASTError("Expected begin of block but got ${it.lexeme} in ${it.position}") }

        while (true) {
            if (context.endOfInput) {
                raise(AstErrorFactory.unexpectedEOF(context.top()))
            }
            if (context.top() is Token.Punctuation.RBrace) {
                break
            }
            statements.add(parseWith(statementParser.value, context))
        }
        match<Token.Punctuation.RBrace>(context.consumeToken()) { ASTError("Expected end of block but got ${it.lexeme} in ${it.position}") }

        return BlockASTNode(statements)
    }
}
