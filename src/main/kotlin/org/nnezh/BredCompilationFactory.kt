package org.nnezh

import arrow.core.Either
import arrow.core.raise.either
import org.nnezh.bred.ast.AbstractSyntaxTreeBuilder
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.codegenerator.TemplateInstantiator
import org.nnezh.bred.common.ExternalFunctionSpecs
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
    private val cTranspilerFactory: () -> CTranspile = { CTranspile() },
    private val standardLibraryProvider: StandardLibraryProvider = StandardLibraryProvider(),
    private val contextCollectorFactory: (List<ExternalFunctionSpecs.ExternalFunctionSpec>) -> ProgramContextCollector =
        { externFunctions -> ProgramContextCollector(externFunctions) },
) {
    fun create(): BredToCCompiler =
        BredToCCompiler(
            lexerFactory = lexerFactory,
            astBuilder = astBuilderFactory(),
            contextCollectorFactory = contextCollectorFactory,
            cTranspiler = cTranspilerFactory(),
            standardLibraryProvider = standardLibraryProvider,
        )
}

class BredToCCompiler(
    private val lexerFactory: (String) -> Lexer,
    private val astBuilder: AbstractSyntaxTreeBuilder,
    private val contextCollectorFactory: (List<ExternalFunctionSpecs.ExternalFunctionSpec>) -> ProgramContextCollector,
    private val cTranspiler: CTranspile,
    private val standardLibraryProvider: StandardLibraryProvider,
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

        val userRoot = parseRoot(source, errorPrefix = null).bind()
        val stdlibRoot = loadStandardLibraryRoot().bind()
        val root = mergeProgramRoots(stdlibRoot, userRoot)

        val initialContext = contextCollectorFactory(standardLibraryProvider.externFunctions).collect(root)
        val resolvedRoot = ProgramTypeResolver(initialContext)
            .resolve(root)
            .mapLeft { listOf(it.describe()) }
            .bind()
        val context = contextCollectorFactory(standardLibraryProvider.externFunctions).collect(resolvedRoot)
        val instantiatedRoot = TemplateInstantiator(context)
            .instantiate(resolvedRoot)
            .mapLeft { listOf(it.describe()) }
            .bind()

        val instantiatedContext = contextCollectorFactory(standardLibraryProvider.externFunctions).collect(instantiatedRoot)
        val resolvedInstantiatedRoot = ProgramTypeResolver(instantiatedContext)
            .resolve(instantiatedRoot)
            .mapLeft { listOf(it.describe()) }
            .bind()
        val analysis = SemanticAnalyzer(instantiatedContext)
            .analyzeWithResult(resolvedInstantiatedRoot)
            .mapLeft { errors -> errors.map { semanticFormatter.format(it) } }
            .bind()

        val tac = Either.catch {
            LLTACGenerator(
                typeTable = analysis.expressionTypes,
                resolvedSymbols = analysis.resolvedSymbols,
                globalContext = instantiatedContext
            ).build(resolvedInstantiatedRoot)
        }.mapLeft { error ->
            listOf("TAC generation failed: ${error.message ?: error::class.qualifiedName}")
        }.bind()

        Either.catch {
            cTranspiler.compile(tac)
        }.mapLeft { error ->
            listOf("C transpilation failed: ${error.message ?: error::class.qualifiedName}")
        }.bind()
    }

    private fun loadStandardLibraryRoot(): Either<List<String>, ProgramRoot> = either {
        val source = readSource(standardLibraryProvider.stdlibPath)
            .mapLeft { listOf("Standard library error: ${it.message}") }
            .bind()

        parseRoot(source, errorPrefix = "Standard library error:").bind()
    }

    private fun parseRoot(source: String, errorPrefix: String?): Either<List<String>, ProgramRoot> = either {
        val tokens = lexerFactory(source)
            .tokenize()
            .mapLeft { listOf(it.describe().withOptionalPrefix(errorPrefix)) }
            .bind()

        val ast = astBuilder
            .build(tokens)
            .mapLeft { listOf(it.describe().withOptionalPrefix(errorPrefix)) }
            .bind()

        ast as? ProgramRoot
            ?: raise(listOf("Parser returned ${ast::class.simpleName}, expected ProgramRoot".withOptionalPrefix(errorPrefix)))
    }

    private fun mergeProgramRoots(stdlibRoot: ProgramRoot, userRoot: ProgramRoot): ProgramRoot =
        ProgramRoot(
            functions = (stdlibRoot.functions + userRoot.functions).toMutableList(),
            globalVariables = stdlibRoot.globalVariables + userRoot.globalVariables,
            types = stdlibRoot.types + userRoot.types,
            typeClasses = stdlibRoot.typeClasses + userRoot.typeClasses,
            instances = stdlibRoot.instances + userRoot.instances,
        )

    private fun LexerError.describe(): String =
        "Lexer error at $position: $message"

    private fun ASTError.describe(): String =
        "Parser error: $message"

    private fun String.withOptionalPrefix(prefix: String?): String =
        prefix?.let { "$it $this" } ?: this

}
