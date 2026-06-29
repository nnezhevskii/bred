package org.nnezh

import arrow.core.Either
import arrow.core.getOrElse
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.nnezh.bred.ast.ASTError
import org.nnezh.bred.ast.ASTNode
import org.nnezh.bred.ast.AbstractSyntaxTreeBuilder
import org.nnezh.bred.ast.ProgramRoot
import org.nnezh.bred.context.ProgramContextCollector
import org.nnezh.lexer.Lexer

internal fun parseProgram(source: String): ProgramRoot {
    val tokens = Lexer(source).tokenize().getOrElse { error("unexpected lexer error: $it") }
    val ast = AbstractSyntaxTreeBuilder().build(tokens).getOrElse { error("unexpected parser error: $it") }
    return assertInstanceOf(ProgramRoot::class.java, ast)
}

internal fun parseEither(source: String): Either<ASTError, ASTNode> {
    val tokens = Lexer(source).tokenize().getOrElse { error("unexpected lexer error: $it") }
    return AbstractSyntaxTreeBuilder().build(tokens)
}

internal fun analyze(source: String): Either<List<SemanticError>, List<SemanticError.SemanticWarning>> {
    val root = parseProgram(source)
    val context = ProgramContextCollector().collect(root)
    var result: Either<List<SemanticError>, List<SemanticError.SemanticWarning>>? = null

    assertDoesNotThrow {
        result = SemanticAnalyzer(context).analyze(root)
    }

    return result ?: error("SemanticAnalyzer did not return a result")
}

internal fun assertNoSemanticDiagnostics(source: String) {
    analyze(source).fold(
        ifLeft = { errors -> error("expected no semantic errors, got: ${errors.describeErrors()}") },
        ifRight = { warnings -> assertTrue(warnings.isEmpty(), "expected no warnings, got: ${warnings.describeWarnings()}") },
    )
}

internal fun assertSemanticError(
    source: String,
    errorType: SemanticErrorType,
    where: Class<out ASTNode>? = null,
) {
    analyze(source).fold(
        ifLeft = { errors ->
            assertTrue(
                errors.any { it.type() == errorType && (where == null || where.isInstance(it.where())) },
                "expected $errorType at ${where?.simpleName ?: "any node"}, got: ${errors.describeErrors()}",
            )
            assertTrue(errors.all { it.isCriticalError }, "expected all errors to be critical")
        },
        ifRight = { warnings -> error("expected semantic error $errorType, got warnings: ${warnings.describeWarnings()}") },
    )
}

internal fun assertSemanticWarning(
    source: String,
    warningType: SemanticErrorType,
    where: Class<out ASTNode>? = null,
) {
    analyze(source).fold(
        ifLeft = { errors -> error("expected warning $warningType, got errors: ${errors.describeErrors()}") },
        ifRight = { warnings ->
            assertTrue(
                warnings.any { it.type == warningType && (where == null || where.isInstance(it.where)) },
                "expected warning $warningType at ${where?.simpleName ?: "any node"}, got: ${warnings.describeWarnings()}",
            )
        },
    )
}

internal fun assertSingleSemanticError(
    source: String,
    errorType: SemanticErrorType,
    where: Class<out ASTNode>? = null,
) {
    analyze(source).fold(
        ifLeft = { errors ->
            assertEquals(1, errors.size, "expected exactly one error, got: ${errors.describeErrors()}")
            assertEquals(errorType, errors.single().type())
            where?.let { assertInstanceOf(it, errors.single().where()) }
            assertTrue(errors.single().isCriticalError)
        },
        ifRight = { warnings -> error("expected semantic error $errorType, got warnings: ${warnings.describeWarnings()}") },
    )
}

private fun SemanticError.type(): SemanticErrorType = when (this) {
    is SemanticError.ControlFlowSemanticError -> errorType
    is SemanticError.FunctionSemanticError -> errorType
    is SemanticError.TypeSemanticError -> errorType
    is SemanticError.VariableScopeSemanticError -> errorType
}

private fun SemanticError.where(): ASTNode = when (this) {
    is SemanticError.ControlFlowSemanticError -> where
    is SemanticError.FunctionSemanticError -> where
    is SemanticError.TypeSemanticError -> where
    is SemanticError.VariableScopeSemanticError -> where
}

private fun List<SemanticError>.describeErrors(): List<Pair<SemanticErrorType, String?>> =
    map { it.type() to it.where()::class.simpleName }

private fun List<SemanticError.SemanticWarning>.describeWarnings(): List<Pair<SemanticErrorType, String?>> =
    map { it.type to it.where::class.simpleName }
