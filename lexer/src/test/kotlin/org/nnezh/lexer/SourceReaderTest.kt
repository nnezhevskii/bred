/*
 * This file was generated with the assistance of AI (Cursor).
 */

package org.nnezh.lexer

import arrow.core.getOrElse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class SourceReaderTest {

    private fun errorOf(path: String): SourceError =
        readSource(path).leftOrNull() ?: error("expected a source error for: $path")

    @Test
    fun `reads existing bred file content`(@TempDir dir: Path) {
        val file = dir.resolve("program.bred")
        file.writeText("val x = 1\n")
        val content = readSource(file.toString()).getOrElse { error("unexpected: $it") }
        assertEquals("val x = 1\n", content)
    }

    @Test
    fun `reads the bundled example`() {
        val content = readSource("examples/max.bred").getOrElse { error("unexpected: $it") }
        assertTrue(content.contains("fun max"))
    }

    @Test
    fun `missing file is rejected`() {
        assertInstanceOf(SourceError.NotFound::class.java, errorOf("does/not/exist.bred"))
    }

    @Test
    fun `directory path is rejected`(@TempDir dir: Path) {
        assertInstanceOf(SourceError.IsDirectory::class.java, errorOf(dir.toString()))
    }

    @Test
    fun `wrong extension is rejected`(@TempDir dir: Path) {
        val file = dir.resolve("program.txt")
        Files.writeString(file, "val x = 1")
        assertInstanceOf(SourceError.WrongExtension::class.java, errorOf(file.toString()))
    }
}
