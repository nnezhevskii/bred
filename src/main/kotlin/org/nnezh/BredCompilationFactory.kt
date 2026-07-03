package org.nnezh

import arrow.core.Either
import arrow.core.raise.either
import org.nnezh.bred.ast.AbstractSyntaxTreeBuilder
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.codegenerator.TemplateInstantiator
import org.nnezh.bred.context.ProgramContextCollector
import org.nnezh.bred.context.ProgramTypeResolver
import org.nnezh.lexer.Lexer
import org.nnezh.lexer.LexerError
import org.nnezh.lexer.readSource
import org.nnezh.org.nnezh.ICGenerator.LLTACGenerator
import org.nnezh.org.nnezh.compiler.CTranspile
import java.nio.file.Path

class BredToCCompilerFactory(
    private val lexerFactory: (String) -> Lexer = { source -> Lexer(source) },
    private val astBuilderFactory: () -> AbstractSyntaxTreeBuilder = { AbstractSyntaxTreeBuilder() },
    private val contextCollectorFactory: () -> ProgramContextCollector = { ProgramContextCollector() },
    private val cTranspilerFactory: () -> CTranspile = { CTranspile() },
) {
    fun create(): BredToCCompiler =
        BredToCCompiler(
            lexerFactory = lexerFactory,
            astBuilder = astBuilderFactory(),
            contextCollectorFactory = contextCollectorFactory,
            cTranspiler = cTranspilerFactory(),
        )
}

class BredToCCompiler(
    private val lexerFactory: (String) -> Lexer,
    private val astBuilder: AbstractSyntaxTreeBuilder,
    private val contextCollectorFactory: () -> ProgramContextCollector,
    private val cTranspiler: CTranspile,
) {
    private val semanticFormatter = SemanticDiagnosticFormatter()

    fun compile(sourceFile: String): Either<List<String>, List<String>> =
        compile(Path.of(sourceFile))

    fun compile(sourceFile: Path): Either<List<String>, List<String>> =
        Either.catch { compileChecked(sourceFile) }
            .fold(
                ifLeft = { error ->
                    Either.Left(listOf("Compilation failed: ${error.message ?: error::class.qualifiedName}"))
                },
                ifRight = { it },
            )

    private fun compileChecked(sourceFile: Path): Either<List<String>, List<String>> = either {
        val source = readSource(sourceFile)
            .mapLeft { listOf(it.message) }
            .bind()

        val tokens = lexerFactory(source)
            .tokenize()
            .mapLeft { listOf(it.describe()) }
            .bind()

        val ast = astBuilder
            .build(tokens)
            .mapLeft { listOf(it.describe()) }
            .bind()
        val root = ast as? ProgramRoot
            ?: raise(listOf("Parser returned ${ast::class.simpleName}, expected ProgramRoot"))

        val initialContext = contextCollectorFactory().collect(root)
        val resolvedRoot = ProgramTypeResolver(initialContext)
            .resolve(root)
            .mapLeft { listOf(it.describe()) }
            .bind()
        val context = contextCollectorFactory().collect(resolvedRoot)
        val instantiatedRoot = TemplateInstantiator(context)
            .instantiate(resolvedRoot)
            .mapLeft { listOf(it.describe()) }
            .bind()

        val instantiatedContext = contextCollectorFactory().collect(instantiatedRoot)
        val resolvedInstantiatedRoot = ProgramTypeResolver(instantiatedContext)
            .resolve(instantiatedRoot)
            .mapLeft { listOf(it.describe()) }
            .bind()
        val analysis = SemanticAnalyzer(instantiatedContext)
            .analyzeWithResult(resolvedInstantiatedRoot)
            .mapLeft { errors -> errors.map { semanticFormatter.format(it) } }
            .bind()

        val tac = Either.catch {
            LLTACGenerator(typeTable = analysis.expressionTypes, globalContext = instantiatedContext).build(resolvedInstantiatedRoot)
        }.mapLeft { error ->
            listOf("TAC generation failed: ${error.message ?: error::class.qualifiedName}")
        }.bind()

        Either.catch {
            cTranspiler.compile(tac)
        }.mapLeft { error ->
            listOf("C transpilation failed: ${error.message ?: error::class.qualifiedName}")
        }.bind()
    }

    private fun LexerError.describe(): String =
        "Lexer error at $position: $message"

    private fun ASTError.describe(): String =
        "Parser error: $message"

}
