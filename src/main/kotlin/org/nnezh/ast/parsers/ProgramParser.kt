package org.nnezh.org.nnezh.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.ast.DeclareFunctionASTNode
import org.nnezh.ast.ImmutableVariableInitializationASTNode
import org.nnezh.ast.ProgramASTNode
import org.nnezh.ast.VariableInitializationASTNode
import org.nnezh.lexer.Token
import org.nnezh.org.nnezh.ast.ASTError
import org.nnezh.org.nnezh.ast.AstErrorFactory
import org.nnezh.org.nnezh.ast.TokensContext

class ProgramParser(
    private val functionParser: Parser<DeclareFunctionASTNode>,
    private val globalVariableParser: Parser<VariableInitializationASTNode>,
) : Parser<ProgramASTNode> {
    override fun Raise<ASTError>.parse(context: TokensContext): ProgramASTNode {
        val functions = mutableListOf<DeclareFunctionASTNode>()
        val globalVariables = mutableListOf<VariableInitializationASTNode>()

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
