package org.nnezh.root

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.nnezh.BredToCCompilerFactory
import org.nnezh.CCompilerInvocationResult
import org.nnezh.CExecutableBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class BredCompilationFactoryTest {
    @Test
    fun `bred compiler returns c lines for valid source file`(@TempDir dir: Path) {
        val source = dir.resolve("main.bred")
        source.writeText("fun main(): Unit { }")

        val result = BredToCCompilerFactory().create().compile(source)

        result.fold(
            ifLeft = { errors -> error("expected C code, got: $errors") },
            ifRight = { cLines ->
                assertTrue(cLines.any { it.contains("int main") }, "expected main function in C output: $cLines")
            },
        )
    }

    @Test
    fun `bred compiler returns parser errors on the left`(@TempDir dir: Path) {
        val source = dir.resolve("broken.bred")
        source.writeText("fun main(: Unit { }")

        val result = BredToCCompilerFactory().create().compile(source)

        result.fold(
            ifLeft = { errors ->
                assertTrue(errors.isNotEmpty())
                assertTrue(errors.any { it.contains("Parser error") }, "expected parser error, got: $errors")
            },
            ifRight = { cLines -> error("expected parser errors, got C code: $cLines") },
        )
    }
}

class CExecutableBuilderTest {
    @Test
    fun `builder prepends runtime writes c file builds exe and deletes intermediate by default`(@TempDir dir: Path) {
        val runtime = dir.resolve("runtime.c")
        val vcvars = dir.resolve("vcvars64.bat")
        val cFile = dir.resolve("out.c")
        val exeFile = dir.resolve("out.exe")
        runtime.writeText("/* runtime */")
        vcvars.writeText("@echo off")

        var cTextSeenByCompiler = ""
        val builder = CExecutableBuilder(
            runtimePath = runtime,
            vcvarsPath = vcvars.toString(),
            compilerInvoker = { _, cPath, exePath ->
                cTextSeenByCompiler = Files.readString(cPath)
                Files.writeString(exePath, "exe")
                CCompilerInvocationResult(0, listOf("compiled with /Od"))
            },
        )

        val messages = builder.build(
            cLines = listOf("int main(void) {", "return 0;", "}"),
            outputExe = exeFile,
            intermediateCFile = cFile,
        )

        assertTrue(Files.exists(exeFile), "expected executable to be created")
        assertTrue(Files.notExists(cFile), "expected intermediate C file to be deleted")
        assertTrue(cTextSeenByCompiler.startsWith("/* runtime */"), cTextSeenByCompiler)
        assertTrue(cTextSeenByCompiler.contains("int main(void) {"), cTextSeenByCompiler)
        assertTrue(messages.any { it.contains("Generated executable") }, "expected success message, got: $messages")
    }

    @Test
    fun `builder keeps intermediate c file when requested`(@TempDir dir: Path) {
        val runtime = dir.resolve("runtime.c")
        val vcvars = dir.resolve("vcvars64.bat")
        val cFile = dir.resolve("kept.c")
        val exeFile = dir.resolve("kept.exe")
        runtime.writeText("/* runtime */")
        vcvars.writeText("@echo off")

        val builder = CExecutableBuilder(
            runtimePath = runtime,
            vcvarsPath = vcvars.toString(),
            compilerInvoker = { _, _, exePath ->
                Files.writeString(exePath, "exe")
                CCompilerInvocationResult(0, emptyList())
            },
        )

        builder.build(
            cLines = listOf("int main(void) {", "return 0;", "}"),
            outputExe = exeFile,
            keepIntermediateC = true,
            intermediateCFile = cFile,
        )

        assertTrue(Files.exists(cFile), "expected intermediate C file to stay")
        assertEquals(
            listOf("/* runtime */", "int main(void) {", "return 0;", "}"),
            Files.readAllLines(cFile),
        )
    }
}
