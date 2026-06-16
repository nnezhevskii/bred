package org.nnezh.ast

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.ASTError
import arrow.core.raise.Raise
import arrow.core.right
import org.nnezh.org.nnezh.Type
import org.nnezh.org.nnezh.ast.AbstractSyntaxTreeExpressionParser
import org.nnezh.org.nnezh.ast.AstErrorFactory
import org.nnezh.org.nnezh.ast.AstErrorFactory.buildError
import org.nnezh.org.nnezh.ast.TokensContext
import org.nnezh.org.nnezh.ast.match

class AbstractSyntaxTreeBuilder(
    private val expressionParser: AbstractSyntaxTreeExpressionParser
) {
    fun build(tokens: List<Token>): Either<ASTError, ASTNode> = either {
        val context = TokensContext(tokens)

        parseProgram(context)
    }

    private fun Raise<ASTError>.parseProgram(context: TokensContext): ProgramASTNode {
        val functions = mutableListOf<DeclareFunctionASTNode>()
        val globalVariables = mutableListOf<ImmutableVariableInitializationASTNode>()

        while (true) {
            if (context.endOfInput) {
                break
            }

            when (context.top()) {
                is Token.Keyword.Fun -> functions.add(parseFunction(context))
                is Token.Keyword.Val -> globalVariables.add(parseGlobalVariable(context))
                else -> raise(AstErrorFactory.expectedFunOrVariableDeclarationError(context.top()))
            }
        }

        return ProgramASTNode(functions, globalVariables)
    }

    private fun Raise<ASTError>.parseFunction(context: TokensContext): DeclareFunctionASTNode {
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
                    val name =
                        match<Token.Identifier>(context.consumeToken()) { token -> ASTError("Expected argument name but got ${token.lexeme} in ${token.position}") }
                    match<Token.Punctuation.Colon>(context.consumeToken()) { token -> ASTError("Expected colon but got ${token.lexeme} in ${token.position}") }
                    val type =
                        match<Token.Identifier>(context.consumeToken()) { token -> ASTError("Expected argument type but got ${token.lexeme} in ${token.position}") }

                    arguments.add(FunctionArgumentASTNode(name.lexeme, Type.fromString(type.lexeme)))
                    argumentWasJustParsed = true

                }

                is Token.Punctuation.RParen -> {
                    context.consumeToken()
                    break
                }

                else -> raise(ASTError("Failed to build arguments in ${context.top().position} got ${context.top().lexeme}"))
            }
        }
        match<Token.Punctuation.Colon>(context.consumeToken()) { token -> ASTError("Expected colon but got ${token.lexeme} in ${token.position}") }
        val type =
            match<Token.Identifier>(context.consumeToken()) { token -> ASTError("Expected return type but got ${token.lexeme} in ${token.position}") }
        val block = parseBlockNode(context)

        return DeclareFunctionASTNode(name.lexeme, FunctionArgsASTNode(arguments), block, type.lexeme)
    }

    private fun Raise<ASTError>.parseBlockNode(context: TokensContext): BlockASTNode {
        val statements = mutableListOf<StatementASTNode>()
        match<Token.Punctuation.LBrace>(context.consumeToken()) { ASTError("Expected begin of block but got ${it.lexeme} in ${it.position}") }

        while (true) {
            if (context.endOfInput) {
                raise(AstErrorFactory.unexpectedEOF(context.top()))
            }
            when (context.top()) {
                is Token.Punctuation.RBrace -> {
                    break
                }

                is Token.Keyword.Val -> {
                    statements.add(parseImmutableInitialization(context))
                }

                is Token.Identifier -> {
                    if (context.top(1) is Token.Punctuation.LParen) {
                        statements.add(parseFunctionCall(context))
                    } else {
                        statements.add(parseAssign(context))
                    }
                }

                is Token.Keyword.If -> {
                    statements.add(parseConditions(context))
                }

                is Token.Keyword.While -> {
                    statements.add(parseWhile(context))
                }

                is Token.Keyword.For -> {
                    statements.add(parseFor(context))
                }

                else -> {
                    raise(ASTError("Didn't expect ${context.top()} in ${context.top().position}"))
                }
            }
        }
        match<Token.Punctuation.RBrace>(context.consumeToken()) { ASTError("Expected end of block but got ${it.lexeme} in ${it.position}") }

        return BlockASTNode(statements)
    }

    private fun Raise<ASTError>.parseConditions(context: TokensContext): StatementASTNode {
        match<Token.Keyword.If>(context.consumeToken()) { buildError("if", it) }
        match<Token.Punctuation.LParen>(context.consumeToken()) { buildError("(", it) }
        val condition: ExpressionASTNode = with(expressionParser) {
            parse(context)
        }
        match<Token.Punctuation.RParen>(context.consumeToken()) { buildError(")", it) }
        val thenSection = parseBlockNode(context)

        val elseBlock: Either<BlockASTNode, EmptyNode> = if (context.top() is Token.Keyword.Else) {
            context.consumeToken()
            parseBlockNode(context).left()
        } else {
            EmptyNode().right()
        }

        return IfStatementASTNode(
            condition = condition,
            thenBlock = thenSection,
            elseBlock = elseBlock
        )
    }

    private fun Raise<ASTError>.parseWhile(context: TokensContext): StatementASTNode {
        match<Token.Keyword.While>(context.consumeToken()) { buildError("while", it) }
        val condition = with(expressionParser) {
            parse(context)
        }
        val block = parseBlockNode(context)

        return WhileStatementASTNode(condition, block)
    }

    private fun Raise<ASTError>.parseFor(context: TokensContext): StatementASTNode {
        match<Token.Keyword.For>(context.consumeToken()) { buildError("for", it) }
        match<Token.Punctuation.LParen>(context.consumeToken()) { buildError("(", it) }
        val counterName = match<Token.Identifier>(context.consumeToken()) { buildError("for-variable", it) }
        match<Token.Keyword.In>(context.consumeToken()) { buildError("in", it) }
        val initialValue = with(expressionParser) {
            parse(context)
        }
        match<Token.Keyword.To>(context.consumeToken()) { buildError("to", it) }
        val finalValue = with(expressionParser) {
            parse(context)
        }
        match<Token.Punctuation.RParen>(context.consumeToken()) { buildError(")", it) }
        val innerBlockStatements = parseBlockNode(context).statements
        val counter = MutableVariableInitializationASTNode(counterName.lexeme, Type.IntType, initialValue)
        val desugared = listOf(
            counter,
            WhileStatementASTNode(
                BinaryExpressionASTNode(
                    VariableExpressionNode(counterName),
                    Token.Operator.Le(context.top().position), // TODO << doesn't seem right. Recalculate? or change arch?
                    finalValue,
                ),
                bodyBlock = BlockASTNode(
                    innerBlockStatements +
                            AssignmentStatementASTNode(
                                counterName.lexeme,
                                BinaryExpressionASTNode(
                                    VariableExpressionNode(counterName),
                                    Token.Operator.Plus(context.top().position),
                                    IntLiteralExpressionNode(1)
                                )
                            )
                ),
            )
        )
        return ForStatementASTNode(BlockASTNode(desugared))
    }

    private fun Raise<ASTError>.parseImmutableInitialization(context: TokensContext): StatementASTNode { // val x = whatever
        match<Token.Keyword.Val>(context.consumeToken()) { token -> ASTError("Expected val but got ${token.lexeme} in ${token.position}") }
        val valName = match<Token.Identifier>(context.consumeToken()) { token -> buildError("variable name", token) }
        match<Token.Punctuation.Colon>(context.consumeToken()) { token -> buildError(":", token) }
        val type = match<Token.Identifier>(context.consumeToken()) { token -> buildError("variable type", token) }

        match<Token.Operator.Assign>(context.consumeToken()) { token -> buildError("assignation", token) }

        val value: ExpressionASTNode = with(expressionParser) {
            parse(context)
        }

        return ImmutableVariableInitializationASTNode(valName.lexeme, Type.fromString(type.lexeme), value)
    }

    private fun Raise<ASTError>.parseAssign(context: TokensContext): StatementASTNode {
        val variableName: String =
            (match<Token.Identifier>(context.consumeToken()) { token -> buildError("variable name", token) }).lexeme
        match<Token.Operator.Assign>(context.consumeToken()) { token -> buildError("assignation", token) }

        val value: ExpressionASTNode = with(expressionParser) {
            parse(context)
        }

        return AssignmentStatementASTNode(variableName, value)
    }

    private fun Raise<ASTError>.parseFunctionCall(context: TokensContext): StatementASTNode {
        return CallFunctionStatementASTNode(with(expressionParser) { parse(context) })
//        val functionName = (match<Token.Identifier>(context.consumeToken()) { token -> buildError("calling function name", token) }).lexeme
//
//        (match<Token.Punctuation.LParen>(context.consumeToken()) { token -> buildError("(", token) }).lexeme
//
//        val arguments = mutableListOf<ExpressionASTNode>()
//        while (context.top() !is Token.Punctuation.RParen) {
//            arguments.add(with(expressionParser) { parse(context) })
//            (match<Token.Punctuation.LParen>(context.consumeToken()) { token -> buildError(",", token) }).lexeme
//        }

    }


    private fun Raise<ASTError>.parseGlobalVariable(context: TokensContext): ImmutableVariableInitializationASTNode {
        TODO()
    }

}