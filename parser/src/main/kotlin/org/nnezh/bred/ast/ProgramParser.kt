package org.nnezh.bred.ast.parsers

import arrow.core.raise.Raise
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.ArrayDeclarationASTNode
import org.nnezh.bred.ast.AstErrorFactory
import org.nnezh.bred.ast.DeclareGlobalVariableASTNode
import org.nnezh.bred.ast.DeclareVariableASTNode
import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.ast.InstanceDeclAstNode
import org.nnezh.bred.ast.Parser
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.ast.ScalarVariableInitializationASTNode
import org.nnezh.bred.ast.TokensContext
import org.nnezh.bred.ast.TypeClassDeclAstNode
import org.nnezh.bred.ast.parseWith
import org.nnezh.lexer.Token

class ProgramParser(
    private val functionParser: Parser<FunctionDeclAstNode>,
    private val globalVariableParser: Parser<DeclareVariableASTNode>,
    private val typeClassParser: TypeClassParser,
    private val instanceParser: InstanceParser,
) : Parser<ProgramRoot> {
    override fun Raise<ASTError>.parse(context: TokensContext): ProgramRoot {
        val functions = mutableListOf<FunctionDeclAstNode>()
        val globalVariables = mutableListOf<DeclareGlobalVariableASTNode>()
        val typeClasses = mutableListOf<TypeClassDeclAstNode>()
        val instances = mutableListOf<InstanceDeclAstNode>()

        while (!context.endOfInput) {
            when (context.top()) {
                is Token.Keyword.Fun -> functions.add(parseWith(functionParser, context))
                is Token.Keyword.Val -> globalVariables.add(parseWith(globalVariableParser, context).toGlobal())
                is Token.Keyword.Typeclass -> typeClasses.add(parseWith(typeClassParser, context))
                is Token.Keyword.Instance -> instances.add(parseWith(instanceParser, context))
                else -> raise(AstErrorFactory.expectedFunOrVariableDeclarationError(context.top()))
            }
        }

        return ProgramRoot(
            functions = functions,
            globalVariables = globalVariables,
            types = emptyList(),
            typeClasses = typeClasses,
            instances = instances,
        )
    }

    private fun DeclareVariableASTNode.toGlobal(): DeclareGlobalVariableASTNode =
        when (this) {
            is ScalarVariableInitializationASTNode -> DeclareGlobalVariableASTNode(
                name = name,
                type = type,
                expression = expression,
                isMutable = isMutable,
            )
            is ArrayDeclarationASTNode -> DeclareGlobalVariableASTNode(
                name = name,
                type = type,
                expression = expression,
                isMutable = isMutable,
                size = size,
            )
        }
}
