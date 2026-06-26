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
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit


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

            val main = CTranspile().compile(tacCode) //.joinToString("\n")
            val runtime = File("runtime.c").readLines()
            val final = runtime + main
            Files.write(Path.of("main.c"), final)
            val vcvarsPath = """C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvars64.bat"""

            val sourceFile = File("main.c")
            val outputFile = File("main.exe")

            if (!sourceFile.exists()) {
                println("Пиздец, а где файл-то? main.c не найден в корне проекта!")
                return
            }

            if (!File(vcvarsPath).exists()) {
                println("Бля, батник MSVC по этому пути отсутствует: $vcvarsPath")
                return
            }

            // Собираем команду для cmd.exe
            // cl.exe берёт main.c и делает из него main.exe (/Fe)
            // Оборачиваем ВСЮ строку после /c в экранированные кавычки "\""
            val command = listOf(
                "cmd.exe", "/c",
                "\"\"$vcvarsPath\" && cl.exe /Od /Fe:\"${outputFile.name}\" \"${sourceFile.name}\"\""
            )

            val process = ProcessBuilder(command)
                .directory(File(".")) // Рабочая папка — корень проекта
                .redirectErrorStream(true)
                .start()

            // Выводим в консоль всё, что думает MSVC о твоём коде
            process.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { line -> println("[MSVC]: $line") }
            }

            val finished = process.waitFor(5, TimeUnit.SECONDS)

            if (finished && process.exitValue() == 0) {
                println("\nDone.")
            } else {
                println("\nException: ${process.exitValue()}")
            }

//            val process = ProcessBuilder(command)
//                .directory(sourceFile.parentFile) // Рабочая папка — там, где лежит исходник
//                .redirectErrorStream(true)        // Сливаем ошибки и обычный вывод в один поток
//                .start()

            // 4. Читаем, что нам выплюнул компилятор (выхлоп cl.exe)
//            process.inputStream.bufferedReader().use { reader ->
//                reader.forEachLine { line -> println("[MSVC LOG]: $line") }
//            }

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