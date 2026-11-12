package org.nnezh.org.nnezh

import arrow.core.raise.either
import org.nnezh.SemanticAnalyzer
import org.nnezh.bred.ast.AbstractSyntaxTreeBuilder
import org.nnezh.bred.ast.AstPrettyPrinter
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.codegenerator.TemplateInstantiator
import org.nnezh.bred.context.ProgramContextCollector
import org.nnezh.bred.context.ProgramTypeResolver
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.readSource
import org.nnezh.org.nnezh.ICGenerator.LLTACGenerator
import org.nnezh.org.nnezh.ICGenerator.PrettyPrinter

fun main(args: Array<String>) {

    val path = args.firstOrNull() ?: "examples/3ac.bred"
    val result = either {
        val source = readSource(path).bind()
        val tokens = Lexer(source).tokenize().bind()

        val stringBuilder = StringBuilder()
        stringBuilder.append("Tokens for '$path':\n")

        for (token in tokens) {
            stringBuilder.append("  ${token.position}\t${token::class.simpleName}\t${token.lexeme}\n")
        }
        val ast = AbstractSyntaxTreeBuilder().build(tokens).bind()

        val root = ast as ProgramRoot
        val initialContext = ProgramContextCollector().collect(root)
        val resolvedRoot = ProgramTypeResolver(initialContext).resolve(root).bind()
        val globalContext = ProgramContextCollector().collect(resolvedRoot)
        val instantiatedAst = TemplateInstantiator(globalContext).instantiate(resolvedRoot).bind()
        val instantiatedContext = ProgramContextCollector().collect(instantiatedAst)
        val resolvedInstantiatedAst = ProgramTypeResolver(instantiatedContext).resolve(instantiatedAst).bind()

        stringBuilder.append("\nAST:\n")
        stringBuilder.append(AstPrettyPrinter().render(resolvedInstantiatedAst))
        stringBuilder.append("\n3AC:\n")
        val analysis = SemanticAnalyzer(instantiatedContext)
            .analyzeWithResult(resolvedInstantiatedAst)
            .bind()

        val tac = LLTACGenerator(typeTable = analysis.expressionTypes, globalContext = instantiatedContext).build(resolvedInstantiatedAst)
        stringBuilder.append(PrettyPrinter().format(tac).joinToString("\n"))
        stringBuilder.toString()
    }

    result.fold(
        ifLeft = { System.err.println(it) },
        ifRight = { println(it) }
    )
}
