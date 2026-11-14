package org.nnezh.root

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.nnezh.DEFAULT_VCVARS64_PATH
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

class CliExecutionTest {
    @Test
    fun `explicit false removes intermediate C and propagates program exit code`(@TempDir dir: Path) {
        val vcvars = System.getenv("VCVARS64_PATH") ?: DEFAULT_VCVARS64_PATH
        assumeTrue(Files.isRegularFile(Path.of(vcvars)), "CLI build requires MSVC")
        val source = dir.resolve("exit.bred")
        source.writeText("fun main(): Int { return 7 }")

        // Gradle exposes the application classpath through its test class loader.
        val classpath = generateSequence(javaClass.classLoader) { it.parent }
            .filterIsInstance<URLClassLoader>()
            .flatMap { it.urLs.asSequence() }
            .filter { it.protocol == "file" }
            .map { File(it.toURI()).absolutePath }
            .plus(System.getProperty("java.class.path").split(File.pathSeparator))
            .distinct().joinToString(File.pathSeparator)
        val log = dir.resolve("cli.log")
        val process = ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
            "-cp", classpath, "org.nnezh.MainKt", source.toString(), dir.resolve("exit.exe").toString(), "false",
        ).redirectErrorStream(true).redirectOutput(log.toFile()).start()
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "CLI timed out")
            assertEquals(7, process.exitValue(), Files.readString(log))
            assertFalse(Files.exists(dir.resolve("exit.c")), "CLI retained intermediate C with keep-c=false")
        } finally {
            if (process.isAlive) process.destroyForcibly().waitFor()
        }
    }
}
