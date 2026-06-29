package org.nnezh

import arrow.core.raise.either
import org.nnezh.bred.ast.AbstractSyntaxTreeBuilder
import org.nnezh.bred.ast.AstPrettyPrinter
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.codegenerator.TemplateInstantiator
import org.nnezh.bred.context.ProgramContextCollector
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.readSource

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

        val globalContext = ProgramContextCollector().collect(ast as ProgramRoot)
        val instantiatedAst = TemplateInstantiator(globalContext).instantiate(ast).bind()

        stringBuilder.append("\nAST:\n")
        stringBuilder.append(AstPrettyPrinter().render(instantiatedAst))
        stringBuilder.toString()

        val newGlobalContext = ProgramContextCollector().collect(instantiatedAst as ProgramRoot)
        SemanticAnalyzer(newGlobalContext).analyze(instantiatedAst)
    }

    result.fold(
        ifLeft = { System.err.println(it) },
        ifRight = { println(it) }
    )
}