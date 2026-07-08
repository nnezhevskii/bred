package org.nnezh.bred.ast

import arrow.core.raise.Raise
import org.nnezh.bred.ast.parsers.TypeSignParser
import org.nnezh.bred.common.TypeRef
import org.nnezh.bred.common.TypeSign
import org.nnezh.lexer.Token

class TypeParser(
    private val typeSignParser: TypeSignParser,
) : Parser<DeclareTypeASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): DeclareTypeASTNode {
        match<Token.Keyword.Type>(context.consumeToken()) { AstErrorFactory.buildError("type", it) }

        var isMutable = false
        if (context.top() is Token.Keyword.Mut) {
            isMutable = true
            context.consumeToken()
        }

        val name = match<Token.Identifier>(context.consumeToken()) { AstErrorFactory.buildError("identifier", it) }

        val genericParams = parseTypeGenericParams(context)
        return when (context.top()) {
            is Token.Punctuation.LParen -> {
                if (genericParams.isNotEmpty()) {
                    raise(ASTError("Product type ${name.lexeme} cannot have generic parameters"))
                }
                parseProductType(context, name.lexeme, isMutable)
            }
            is Token.Operator.Assign -> {
                if (isMutable) {
                    raise(ASTError("Sum type ${name.lexeme} cannot be declared with mut"))
                }
                parseSumType(context, name.lexeme, genericParams)
            }
            else -> raise(AstErrorFactory.buildError("'(' or '='", context.top()))
        }
    }

    private fun Raise<ASTError>.parseProductType(
        context: TokensContext,
        typeName: String,
        isMutable: Boolean,
    ): DeclareProductTypeASTNode {
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
            typeName,
            isMutable,
            fields,
        )
    }

    private fun Raise<ASTError>.parseSumType(
        context: TokensContext,
        typeName: String,
        genericParams: List<GenericParam>,
    ): DeclareSumTypeASTNode {
        match<Token.Operator.Assign>(context.consumeToken()) { AstErrorFactory.buildError("=", it) }

        val variants = mutableListOf<SumTypeVariantASTNode>()
        val variantNames = mutableSetOf<String>()
        while (true) {
            val variant = parseSumTypeVariant(context)
            if (!variantNames.add(variant.name)) {
                raise(ASTError("${variant.name} is not unique variant for $typeName"))
            }
            variants += variant

            if (context.top() is Token.Punctuation.Pipe) {
                context.consumeToken()
                continue
            }
            break
        }

        return DeclareSumTypeASTNode(
            name = typeName,
            genericParams = genericParams,
            variants = variants,
        )
    }

    private fun Raise<ASTError>.parseSumTypeVariant(context: TokensContext): SumTypeVariantASTNode {
        val name = match<Token.Identifier>(context.consumeToken()) { AstErrorFactory.buildError("variant name", it) }
        val typeArguments = parseVariantTypeArguments(context)
        val fields = if (context.top() is Token.Punctuation.LParen) {
            parseVariantFields(context, name.lexeme)
        } else {
            emptyList()
        }

        return SumTypeVariantASTNode(
            name = name.lexeme,
            typeArguments = typeArguments,
            fields = fields,
        )
    }

    private fun Raise<ASTError>.parseVariantTypeArguments(context: TokensContext): List<TypeSign> {
        if (context.top() !is Token.Operator.Lt) {
            return emptyList()
        }
        context.consumeToken()
        val arguments = mutableListOf<TypeSign>()
        if (context.top() is Token.Operator.Gt) {
            raise(AstErrorFactory.buildError("type argument", context.top()))
        }
        while (true) {
            arguments += with(typeSignParser) { parse(context) }
            when (context.top()) {
                is Token.Punctuation.Comma -> context.consumeToken()
                is Token.Operator.Gt -> {
                    context.consumeToken()
                    break
                }
                else -> raise(AstErrorFactory.buildError("',' or '>'", context.top()))
            }
        }
        return arguments
    }

    private fun Raise<ASTError>.parseVariantFields(
        context: TokensContext,
        variantName: String,
    ): List<SumTypeVariantFieldASTNode> {
        match<Token.Punctuation.LParen>(context.consumeToken()) { AstErrorFactory.buildError("(", it) }
        val fields = mutableListOf<SumTypeVariantFieldASTNode>()
        val fieldNames = mutableSetOf<String>()
        if (context.top() is Token.Punctuation.RParen) {
            context.consumeToken()
            return fields
        }

        while (true) {
            val name = match<Token.Identifier>(context.consumeToken()) { AstErrorFactory.buildError("field name", it) }
            match<Token.Punctuation.Colon>(context.consumeToken()) { AstErrorFactory.buildError(":", it) }
            if (!fieldNames.add(name.lexeme)) {
                raise(ASTError("${name.lexeme} is not unique field for variant $variantName"))
            }
            fields += SumTypeVariantFieldASTNode(name.lexeme, with(typeSignParser) { parse(context) })

            when (context.top()) {
                is Token.Punctuation.Comma -> {
                    context.consumeToken()
                    match<Token.Identifier>(context.top()) { AstErrorFactory.buildError("field name", it) }
                }
                is Token.Punctuation.RParen -> {
                    context.consumeToken()
                    break
                }
                else -> raise(AstErrorFactory.buildError("',' or ')'", context.top()))
            }
        }

        return fields
    }

    private fun Raise<ASTError>.parseTypeGenericParams(context: TokensContext): List<GenericParam> {
        if (context.top() !is Token.Operator.Lt) {
            return emptyList()
        }
        context.consumeToken()
        val params = mutableListOf<GenericParam>()
        if (context.top() is Token.Operator.Gt) {
            raise(AstErrorFactory.buildError("generic parameter", context.top()))
        }
        while (true) {
            val name = match<Token.Identifier>(context.consumeToken()) {
                AstErrorFactory.buildError("generic parameter", it)
            }
            params += GenericParam(name.lexeme, emptyList())
            when (context.top()) {
                is Token.Punctuation.Comma -> context.consumeToken()
                is Token.Operator.Gt -> {
                    context.consumeToken()
                    break
                }
                else -> raise(AstErrorFactory.buildError("',' or '>'", context.top()))
            }
        }
        return params
    }
}
