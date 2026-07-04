package org.nnezh.bred.ast

import arrow.core.raise.Raise
import org.nnezh.bred.common.TypeRef
import org.nnezh.bred.common.TypeSign
import org.nnezh.lexer.Token
import kotlin.math.min

class TypeParser: Parser<DeclareTypeASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): DeclareTypeASTNode {
        match<Token.Keyword.Type>(context.consumeToken()) { AstErrorFactory.buildError("type", it) }

        var isMutable = false
        if (context.top() is Token.Keyword.Mut) {
            isMutable = true
            context.consumeToken()
        }

        val name = match<Token.Identifier>(context.consumeToken()) { AstErrorFactory.buildError("identifier", it) }

        match<Token.Punctuation.LParen>(context.consumeToken()) { AstErrorFactory.buildError("(", it) }

        val fields: MutableMap<String, TypeSign> = mutableMapOf()

        while (true) {

            if (context.top() is Token.Punctuation.RParen) {
                context.consumeToken()
                break
            }

            val name = match<Token.Identifier>(context.consumeToken()) { AstErrorFactory.buildError("identifier", it) }
            match<Token.Punctuation.Colon>(context.consumeToken()) { AstErrorFactory.buildError("field type declaration", it) }
            val type = match<Token.Identifier>(context.consumeToken()) { AstErrorFactory.buildError("identifier", it) }

            if (fields.containsKey(name.lexeme)) {
                raise(ASTError("${name.lexeme} is not unique field for ${name.lexeme}"))
            }
            fields[name.lexeme] = TypeRef(type.lexeme)

            if (context.top() is Token.Punctuation.Comma) {
                context.consumeToken()
                match<Token.Identifier>(context.top()) { AstErrorFactory.buildError("identifier", it) }

                continue
            }
        }

        return DeclareProductTypeASTNode(
            name.lexeme,
            isMutable,
            fields,
        )

    }
}