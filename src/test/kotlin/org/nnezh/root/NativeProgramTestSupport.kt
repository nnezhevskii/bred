package org.nnezh.root

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import org.nnezh.BredToCCompilerFactory
import org.nnezh.CExecutableBuilder
import org.nnezh.DEFAULT_VCVARS64_PATH
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

abstract class NativeProgramTestSupport {
    @TempDir
    lateinit var dir: Path

    protected fun assertOutput(program: String, expected: List<String>, expectedExit: Int = 0) {
        val vcvars = System.getenv("VCVARS64_PATH") ?: DEFAULT_VCVARS64_PATH
        assumeTrue(Files.isRegularFile(Path.of(vcvars)), "Native execution requires MSVC")
        val source = dir.resolve("program.bred")
        source.writeText(program.trimIndent())
        val cLines = BredToCCompilerFactory().create().compile(source).fold(
            ifLeft = { errors -> error(errors.joinToString("\n")) },
            ifRight = { it },
        )
        val exe = dir.resolve("program.exe")
        val messages = CExecutableBuilder(vcvarsPath = vcvars).build(cLines, exe)
        assertTrue(Files.isRegularFile(exe), messages.joinToString("\n"))

        val output = dir.resolve("output.txt")
        val process = ProcessBuilder(exe.toString()).redirectErrorStream(true).redirectOutput(output.toFile()).start()
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Program timed out")
            val lines = Files.readAllLines(output)
            assertEquals(expectedExit, process.exitValue(), lines.joinToString("\n"))
            assertEquals(expected, lines)
        } finally {
            if (process.isAlive) process.destroyForcibly().waitFor()
        }
    }
}
