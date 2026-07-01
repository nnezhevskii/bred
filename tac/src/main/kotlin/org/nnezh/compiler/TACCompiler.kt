package org.nnezh.org.nnezh.compiler

import arrow.core.getOrElse
import org.nnezh.SemanticAnalyzer
import org.nnezh.bred.ast.AbstractSyntaxTreeBuilder
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.codegenerator.TemplateInstantiator
import org.nnezh.bred.context.ProgramContextCollector
import org.nnezh.lexer.Lexer
import org.nnezh.org.nnezh.ICGenerator.LLTACElement
import org.nnezh.org.nnezh.ICGenerator.LLTACGenerator

interface TACCompiler {
    fun compile(src: String): List<LLTACElement>
}

class TACCompilerImpl : TACCompiler {
    override fun compile(src: String): List<LLTACElement> {
        val tokens = Lexer(src).tokenize().getOrElse { error("unexpected lexer error: $it") }
        val ast = AbstractSyntaxTreeBuilder().build(tokens).getOrElse { error("unexpected parse error: $it") }
        val root = ast as? ProgramRoot ?: error("unexpected parse error: expected ProgramRoot, got $ast")

        val globalContext = ProgramContextCollector().collect(root)
        val instantiatedRoot = TemplateInstantiator(globalContext)
            .instantiate(root)
            .getOrElse { error("unexpected template instantiation error: $it") }

        val instantiatedContext = ProgramContextCollector().collect(instantiatedRoot)
        val analysis = SemanticAnalyzer(instantiatedContext)
            .analyzeWithResult(instantiatedRoot)
            .getOrElse { error("semantic analysis failed: $it") }

        return LLTACGenerator(typeTable = analysis.expressionTypes).build(instantiatedRoot)
    }
}
