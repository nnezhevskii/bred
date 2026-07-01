package org.nnezh

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

const val DEFAULT_VCVARS64_PATH: String =
    "C:\\Program Files\\Microsoft Visual Studio\\18\\Community\\VC\\Auxiliary\\Build\\vcvars64.bat"

data class CCompilerInvocationResult(
    val exitCode: Int,
    val messages: List<String>,
)

fun interface CCompilerInvoker {
    fun invoke(
        vcvarsPath: String,
        cFile: Path,
        exeFile: Path,
    ): CCompilerInvocationResult
}

class MsvcCCompilerInvoker : CCompilerInvoker {
    override fun invoke(
        vcvarsPath: String,
        cFile: Path,
        exeFile: Path,
    ): CCompilerInvocationResult {
        val command = listOf(
            "cmd.exe",
            "/c",
            "\"\"$vcvarsPath\" && cl.exe /nologo /Od /Fe:\"${exeFile.toAbsolutePath()}\" \"${cFile.toAbsolutePath()}\"\"",
        )

        val process = ProcessBuilder(command)
            .directory(cFile.parent.toFile())
            .redirectErrorStream(true)
            .start()

        val messages = process.inputStream.bufferedReader().use { it.readLines() }
        val exitCode = process.waitFor()
        return CCompilerInvocationResult(exitCode, messages)
    }
}

class CExecutableBuilder(
    private val runtimePath: Path = Path.of("runtime.c"),
    private val vcvarsPath: String = System.getenv("VCVARS64_PATH") ?: DEFAULT_VCVARS64_PATH,
    private val compilerInvoker: CCompilerInvoker = MsvcCCompilerInvoker(),
) {
    fun build(
        cLines: List<String>,
        outputExe: String,
        keepIntermediateC: Boolean = false,
        intermediateCFile: Path? = null,
    ): List<String> =
        build(cLines, Path.of(outputExe), keepIntermediateC, intermediateCFile)

    fun build(
        cLines: List<String>,
        outputExe: Path,
        keepIntermediateC: Boolean = false,
        intermediateCFile: Path? = null,
    ): List<String> {
        val messages = mutableListOf<String>()
        val exeFile = normalizeExePath(outputExe).toAbsolutePath()
        val cFile = (intermediateCFile ?: exeFile.resolveSibling("${exeFile.nameWithoutExtension}.c")).toAbsolutePath()

        try {
            val vcvarsFile = Path.of(vcvarsPath)
            if (!Files.exists(vcvarsFile)) {
                return listOf("MSVC vcvars64.bat not found: $vcvarsPath")
            }
            if (!Files.exists(runtimePath)) {
                return listOf("Runtime C file does not exist: ${runtimePath.toAbsolutePath()}")
            }

            cFile.parent?.let { Files.createDirectories(it) }
            exeFile.parent?.let { Files.createDirectories(it) }

            val runtimeLines = Files.readAllLines(runtimePath)
            Files.write(cFile, runtimeLines + cLines)
            messages.add("Generated intermediate C file: $cFile")

            val invocation = compilerInvoker.invoke(vcvarsPath, cFile, exeFile)
            messages.addAll(invocation.messages)

            if (invocation.exitCode != 0) {
                messages.add("MSVC cl.exe failed with exit code ${invocation.exitCode}")
                return messages
            }

            val producedExe = cFile.parent.resolve(exeFile.fileName)
            if (producedExe != exeFile && Files.exists(producedExe)) {
                Files.move(producedExe, exeFile, StandardCopyOption.REPLACE_EXISTING)
            }
            if (!Files.exists(exeFile)) {
                messages.add("MSVC cl.exe finished successfully but executable was not found: $exeFile")
                return messages
            }

            messages.add("Generated executable: $exeFile")
            return messages
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            return messages + "C compiler invocation was interrupted: ${error.message ?: error::class.qualifiedName}"
        } catch (error: IOException) {
            return messages + "I/O error while building executable: ${error.message ?: error::class.qualifiedName}"
        } catch (error: Throwable) {
            return messages + "Unexpected error while building executable: ${error.message ?: error::class.qualifiedName}"
        } finally {
            if (!keepIntermediateC) {
                try {
                    Files.deleteIfExists(cFile)
                } catch (error: IOException) {
                    messages.add("Failed to delete intermediate C file $cFile: ${error.message}")
                }
            }
        }
    }

    private fun normalizeExePath(outputExe: Path): Path =
        if (outputExe.extension.equals("exe", ignoreCase = true)) {
            outputExe
        } else {
            outputExe.resolveSibling("${outputExe.fileName}.exe")
        }
}
