package org.nnezh.org.nnezh.compiler

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.right
import org.nnezh.ast.AbstractSyntaxTreeBuilder
import org.nnezh.ast.ProgramASTNode
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.readSource
import org.nnezh.org.nnezh.ICGenerator.LLTACElement
import org.nnezh.org.nnezh.ICGenerator.LLTACGenerator
import org.nnezh.org.nnezh.ast.AbstractSyntaxTreeExpressionParser
import org.nnezh.org.nnezh.semantic.SemanticAnalyzer

class TACCompiler {
    fun compile(src: String): List<LLTACElement> {
        val tokens = Lexer(src).tokenize().getOrElse { error("unexpected lexer error: $it") }
        val ast = AbstractSyntaxTreeBuilder().build(tokens).getOrElse { error("unexpected parse error: $it") }
        val semanticAnalyzer = SemanticAnalyzer().also { analyzer ->
            val errors = analyzer.invoke(ast as ProgramASTNode)
            if (errors.any { it.isCriticalError }) {
                error("unexpected parse error: $errors")
            }
        }
        val tacGenerator = LLTACGenerator(
            typeTable = semanticAnalyzer.typeTable,
            functionRegistry = semanticAnalyzer.functionRegistry
        )
        return tacGenerator.build(ast)
    }
}