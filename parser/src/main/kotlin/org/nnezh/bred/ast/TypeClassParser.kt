package org.nnezh.bred.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.AstErrorFactory
import org.nnezh.bred.ast.FunctionArgument
import org.nnezh.bred.ast.GenericParam
import org.nnezh.bred.ast.Parser
import org.nnezh.bred.ast.TokensContext
import org.nnezh.bred.ast.TypeClassDeclAstNode
import org.nnezh.bred.ast.TypeClassMethodDeclAstNode
import org.nnezh.bred.ast.TypeSign
import org.nnezh.bred.ast.match
import org.nnezh.lexer.Token

class TypeClassParser(
    private val typeSignParser: TypeSignParser = TypeSignParser(),
) : Parser<TypeClassDeclAstNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): TypeClassDeclAstNode {
        match<Token.Keyword.Typeclass>(context.consumeToken()) { AstErrorFactory.buildError("typeclass", it) }
        val name = match<Token.Identifier>(context.consumeToken()) { AstErrorFactory.buildError("typeclass name", it) }
        val genericParam = parseSingleGenericParam(context)
        match<Token.Punctuation.LBrace>(context.consumeToken()) { AstErrorFactory.buildError("{", it) }
        val methods = mutableListOf<TypeClassMethodDeclAstNode>()
        while (context.top() !is Token.Punctuation.RBrace) {
            if (context.endOfInput) {
                raise(AstErrorFactory.unexpectedEOF(context.top()))
            }
            methods += parseMethodSignature(context)
        }
        match<Token.Punctuation.RBrace>(context.consumeToken()) { AstErrorFactory.buildError("}", it) }
        return TypeClassDeclAstNode(name.lexeme, genericParam, methods)
    }

    private fun Raise<ASTError>.parseSingleGenericParam(context: TokensContext): GenericParam {
        match<Token.Operator.Lt>(context.consumeToken()) { AstErrorFactory.buildError("<", it) }
        val name = match<Token.Identifier>(context.consumeToken()) { AstErrorFactory.buildError("generic parameter", it) }
        match<Token.Operator.Gt>(context.consumeToken()) { AstErrorFactory.buildError(">", it) }
        return GenericParam(name.lexeme, emptyList())
    }

    private fun Raise<ASTError>.parseMethodSignature(context: TokensContext): TypeClassMethodDeclAstNode {
        match<Token.Keyword.Fun>(context.consumeToken()) { AstErrorFactory.buildError("fun", it) }
        val name = match<Token.Identifier>(context.consumeToken()) { AstErrorFactory.buildError("method name", it) }
        val args: List<FunctionArgument> = with(typeSignParser) { parseArguments(context) }
        val result = if (context.top() is Token.Punctuation.Colon) {
            context.consumeToken()
            with(typeSignParser) { parse(context) }
        } else {
            TypeSign("Unit")
        }
        return TypeClassMethodDeclAstNode(name.lexeme, args, result)
    }
}
