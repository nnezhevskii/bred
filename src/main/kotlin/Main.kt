package org.nnezh

import arrow.core.raise.context.bind
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.readSource
import arrow.core.raise.either
import org.nnezh.ast.AbstractSyntaxTreeBuilder
import org.nnezh.ast.ProgramASTNode
import org.nnezh.org.nnezh.ICGenerator.LLTACElement
import org.nnezh.org.nnezh.ICGenerator.LLTACFunc
import org.nnezh.org.nnezh.ICGenerator.LLTACGenerator
import org.nnezh.org.nnezh.ICGenerator.LLTACLabel
import org.nnezh.org.nnezh.ICGenerator.PrettyPrinter
import org.nnezh.org.nnezh.ast.AbstractSyntaxTreeExpressionParser
import org.nnezh.org.nnezh.semantic.SemanticAnalyzer
import org.nnezh.org.nnezh.semantic.analyzers.FunctionSubAnalyzer
import org.nnezh.org.nnezh.semantic.analyzers.VariableScopeSubAnalyzer
import org.nnezh.org.nnezh.semantic.generic.SemanticError
import kotlin.random.Random



// TODO: VARIABLE_CHANGING_IMMUTABLE -Необходимо добавить тесты
// TODO - добавить проверку, что функция main() существует
fun main(args: Array<String>) {

    val path = args.firstOrNull() ?: "examples/3ac.bred"
    val result = either {
        val source = readSource(path).bind()
        val tokens = Lexer(source).tokenize().bind()

        val stringBuilder = StringBuilder()
        stringBuilder.append("Tokens for '$path':")

        for (token in tokens) {
            stringBuilder.append("  ${token.position}\t${token::class.simpleName}\t${token.lexeme}\n")
        }
        stringBuilder.toString()

        val ast = AbstractSyntaxTreeBuilder(AbstractSyntaxTreeExpressionParser()).build(tokens).bind()

        val semanticAnalyzer = SemanticAnalyzer()
        val res = semanticAnalyzer(ast as ProgramASTNode)

        res.joinToString("\n").ifEmpty { "<NoErrors>" }
        if (res.any { it.isCriticalError }) {
            res.joinToString("\n")
        } else {
            val tacGenerator = LLTACGenerator(
                typeTable = semanticAnalyzer.typeTable,
                functionRegistry = semanticAnalyzer.functionRegistry
            )
            PrettyPrinter().format (tacGenerator.build(ast)).joinToString("\n")
        }

    }
    /*
    TODO: был найден баг по тайпчекингу ретурна. Нужно дописать тесты
    fun getQuarter(a: Double, b: Double): String {
    if (a > 0) {
        return "2"
    } else {
        return a - b
    }
}
     */

    result.fold(
        ifLeft = { System.err.println(it) },
        ifRight = { println(it) }
    )
}
