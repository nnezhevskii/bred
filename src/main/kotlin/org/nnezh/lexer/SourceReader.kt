/*
 * This file was generated with the assistance of AI (Cursor).
 */

package org.nnezh.lexer

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

/** Canonical file extension for bred source files. */
const val BRED_EXTENSION: String = "bred"

/**
 * Typed errors that may occur while reading a bred source file. Returned via
 * [Either] instead of being thrown.
 */
sealed interface SourceError {
    val path: Path
    val message: String

    data class NotFound(override val path: Path) : SourceError {
        override val message: String get() = "Source file does not exist: $path"
    }

    data class IsDirectory(override val path: Path) : SourceError {
        override val message: String get() = "Source path is a directory, not a file: $path"
    }

    data class WrongExtension(override val path: Path) : SourceError {
        override val message: String get() = "Expected a .$BRED_EXTENSION file but got: $path"
    }

    data class ReadFailure(override val path: Path, val cause: Throwable) : SourceError {
        override val message: String get() = "Failed to read $path: ${cause.message}"
    }
}

/**
 * Reads the whole content of a bred source file by [path], returning either the
 * content or a [SourceError].
 */
fun readSource(path: String): Either<SourceError, String> = readSource(Path.of(path))

/**
 * Reads the whole content of a bred source file located at [path], returning
 * either the content or a [SourceError].
 */
fun readSource(path: Path): Either<SourceError, String> = either {
    ensure(Files.exists(path)) { SourceError.NotFound(path) }
    ensure(!Files.isDirectory(path)) { SourceError.IsDirectory(path) }
    ensure(path.extension == BRED_EXTENSION) { SourceError.WrongExtension(path) }
    Either.catch { Files.readString(path) }
        .mapLeft { SourceError.ReadFailure(path, it) }
        .bind()
}
