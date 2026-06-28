package org.nnezh.bred.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.AstErrorFactory
import org.nnezh.bred.ast.BlockAstNode
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.InstanceDeclAstNode
import org.nnezh.bred.ast.Parser
import org.nnezh.bred.ast.TokensContext
import org.nnezh.bred.ast.match
import org.nnezh.lexer.Token

class InstanceParser(
    private val blockParser: Lazy<Parser<BlockAstNode>>,
    private val typeSignParser: TypeSignParser = TypeSignParser(),
) : Parser<InstanceDeclAstNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): InstanceDeclAstNode {
        match<Token.Keyword.Instance>(context.consumeToken()) { AstErrorFactory.buildError("instance", it) }
        val instanceType = with(typeSignParser) { parse(context) }
        if (instanceType.args.size != 1) {
            raise(AstErrorFactory.buildError("single instance type argument", context.top()))
        }
        match<Token.Punctuation.LBrace>(context.consumeToken()) { AstErrorFactory.buildError("{", it) }
        val methods = mutableListOf<FunctionDeclAstNode>()
        val functionParser = FunctionParser(blockParser, typeSignParser)
        while (context.top() !is Token.Punctuation.RBrace) {
            if (context.endOfInput) {
                raise(AstErrorFactory.unexpectedEOF(context.top()))
            }
            methods += with(functionParser) { parse(context) }
        }
        match<Token.Punctuation.RBrace>(context.consumeToken()) { AstErrorFactory.buildError("}", it) }
        return InstanceDeclAstNode(instanceType.name, instanceType.args.single(), methods)
    }
}
