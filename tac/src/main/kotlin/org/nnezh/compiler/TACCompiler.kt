package org.nnezh.org.nnezh.compiler

import arrow.core.getOrElse
import org.nnezh.SemanticAnalyzer
import org.nnezh.SemanticDiagnosticFormatter
import org.nnezh.bred.ast.AbstractSyntaxTreeBuilder
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.codegenerator.TemplateInstantiator
import org.nnezh.bred.context.ProgramContextCollector
import org.nnezh.bred.context.ProgramTypeResolver
import org.nnezh.lexer.Lexer
import org.nnezh.org.nnezh.ICGenerator.LLTACElement
import org.nnezh.org.nnezh.ICGenerator.LLTACGenerator

interface TACCompiler {
    fun compile(src: String): List<LLTACElement>
}

class TACCompilerImpl : TACCompiler {
    private val semanticFormatter = SemanticDiagnosticFormatter()

    override fun compile(src: String): List<LLTACElement> {
        val tokens = Lexer(src).tokenize().getOrElse { error("unexpected lexer error: $it") }
        val ast = AbstractSyntaxTreeBuilder().build(tokens).getOrElse { error("unexpected parse error: $it") }
        val root = ast as? ProgramRoot ?: error("unexpected parse error: expected ProgramRoot, got $ast")

        val initialContext = ProgramContextCollector().collect(root)
        val resolvedRoot = ProgramTypeResolver(initialContext)
            .resolve(root)
            .getOrElse { error("unexpected type resolution error: $it") }
        val globalContext = ProgramContextCollector().collect(resolvedRoot)
        val instantiatedRoot = TemplateInstantiator(globalContext)
            .instantiate(resolvedRoot)
            .getOrElse { error("unexpected template instantiation error: $it") }

        val instantiatedContext = ProgramContextCollector().collect(instantiatedRoot)
        val resolvedInstantiatedRoot = ProgramTypeResolver(instantiatedContext)
            .resolve(instantiatedRoot)
            .getOrElse { error("unexpected type resolution error: $it") }
        val analysis = SemanticAnalyzer(instantiatedContext)
            .analyzeWithResult(resolvedInstantiatedRoot)
            .getOrElse { errors ->
                error("semantic analysis failed:\n${errors.joinToString("\n") { semanticFormatter.format(it) }}")
            }

        return LLTACGenerator(
            typeTable = analysis.expressionTypes,
            resolvedSymbols = analysis.resolvedSymbols,
            globalContext = instantiatedContext).build(resolvedInstantiatedRoot)
    }
}
