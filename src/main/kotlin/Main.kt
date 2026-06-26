package org.nnezh

import arrow.core.raise.context.bind
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.readSource
import arrow.core.raise.either
import org.nnezh.ast.AbstractSyntaxTreeBuilder
import org.nnezh.ast.ProgramASTNode
import org.nnezh.org.nnezh.ICGenerator.LLTACGenerator
import org.nnezh.org.nnezh.ICGenerator.PrettyPrinter
import org.nnezh.org.nnezh.ast.AbstractSyntaxTreeExpressionParser
import org.nnezh.org.nnezh.compiler.CTranspile
import org.nnezh.org.nnezh.semantic.SemanticAnalyzer



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
        if (res.any { it.isCriticalError }) {
            res.joinToString("\n")
        } else {
            val tacGenerator = LLTACGenerator(
                typeTable = semanticAnalyzer.typeTable,
                functionRegistry = semanticAnalyzer.functionRegistry
            )

            val tacCode = tacGenerator.build(ast)

            CTranspile().compile(tacCode).joinToString("\n")
//            tacCode.joinToString("\n")

//            PrettyPrinter().format (tacGenerator.build(ast)).joinToString("\n")
        }

//        res.joinToString("\n").ifEmpty { "<NoErrors>" }
//        if (res.any { it.isCriticalError }) {
//            res.joinToString("\n")
//        } else {

//        }

    }

    result.fold(
        ifLeft = { System.err.println(it) },
        ifRight = { println(it) }
    )
}
/**
 * TODO: написать снэпшот тест на while
 */