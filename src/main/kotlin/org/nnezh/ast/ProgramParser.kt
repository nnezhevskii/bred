package org.nnezh.org.nnezh.ast

import arrow.core.raise.Raise
import org.nnezh.ast.DeclareFunctionASTNode
import org.nnezh.ast.ImmutableVariableInitializationASTNode
import org.nnezh.ast.ProgramASTNode
import org.nnezh.lexer.Token

class ProgramParser(
    private val functionParser: Parser<DeclareFunctionASTNode>,
    private val globalVariableParser: Parser<ImmutableVariableInitializationASTNode>,
) : Parser<ProgramASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): ProgramASTNode {
        val functions = mutableListOf<DeclareFunctionASTNode>()
        val globalVariables = mutableListOf<ImmutableVariableInitializationASTNode>()

        while (true) {
            if (context.endOfInput) {
                break
            }

            when (context.top()) {
                is Token.Keyword.Fun -> functions.add(parseWith(functionParser, context))
                is Token.Keyword.Val -> globalVariables.add(parseWith(globalVariableParser, context))
                else -> raise(AstErrorFactory.expectedFunOrVariableDeclarationError(context.top()))
            }
        }

        return ProgramASTNode(functions, globalVariables)
    }
}
