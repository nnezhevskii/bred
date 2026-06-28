package org.nnezh.bred.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.AstErrorFactory
import org.nnezh.bred.ast.FunctionArgument
import org.nnezh.bred.ast.GenericParam
import org.nnezh.bred.ast.TokensContext
import org.nnezh.bred.ast.TypeSign
import org.nnezh.bred.ast.match
import org.nnezh.lexer.Token

class TypeSignParser {
    fun Raise<ASTError>.parse(context: TokensContext): TypeSign {
        val name = match<Token.Identifier>(context.consumeToken()) { AstErrorFactory.buildError("type name", it) }
        val args = if (context.top() is Token.Operator.Lt) {
            context.consumeToken()
            val parsedArgs = mutableListOf<TypeSign>()
            if (context.top() is Token.Operator.Gt) {
                raise(AstErrorFactory.buildError("type argument", context.top()))
            }
            while (true) {
                parsedArgs += parse(context)
                when (context.top()) {
                    is Token.Punctuation.Comma -> context.consumeToken()
                    is Token.Operator.Gt -> {
                        context.consumeToken()
                        break
                    }
                    else -> raise(AstErrorFactory.buildError("',' or '>'", context.top()))
                }
            }
            parsedArgs
        } else {
            emptyList()
        }
        return TypeSign(name.lexeme, args)
    }

    fun Raise<ASTError>.parseGenericParams(context: TokensContext): List<GenericParam> {
        if (context.top() !is Token.Operator.Lt) {
            return emptyList()
        }
        context.consumeToken()
        val params = mutableListOf<GenericParam>()
        while (true) {
            val name = match<Token.Identifier>(context.consumeToken()) { AstErrorFactory.buildError("generic parameter", it) }
            val constraints = mutableListOf<String>()
            if (context.top() is Token.Punctuation.Colon) {
                context.consumeToken()
                constraints += match<Token.Identifier>(context.consumeToken()) {
                    AstErrorFactory.buildError("generic constraint", it)
                }.lexeme
            }
            params += GenericParam(name.lexeme, constraints)
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

    fun Raise<ASTError>.parseArguments(context: TokensContext): List<FunctionArgument> {
        match<Token.Punctuation.LParen>(context.consumeToken()) { AstErrorFactory.buildError("(", it) }
        val arguments = mutableListOf<FunctionArgument>()
        if (context.top() is Token.Punctuation.RParen) {
            context.consumeToken()
            return arguments
        }
        while (true) {
            val name = match<Token.Identifier>(context.consumeToken()) { AstErrorFactory.buildError("argument name", it) }
            match<Token.Punctuation.Colon>(context.consumeToken()) { AstErrorFactory.buildError(":", it) }
            val type = parse(context)
            val isArray = if (context.top() is Token.Punctuation.LBracket) {
                context.consumeToken()
                match<Token.Punctuation.RBracket>(context.consumeToken()) { AstErrorFactory.buildError("]", it) }
                true
            } else {
                false
            }
            arguments += FunctionArgument(name.lexeme, type, isArray)
            when (context.top()) {
                is Token.Punctuation.Comma -> {
                    context.consumeToken()
                    match<Token.Identifier>(context.top()) { AstErrorFactory.buildError("next argument", it) }
                }
                is Token.Punctuation.RParen -> {
                    context.consumeToken()
                    break
                }
                else -> raise(AstErrorFactory.buildError("',' or ')'", context.top()))
            }
        }
        return arguments
    }
}
