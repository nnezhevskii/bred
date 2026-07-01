package org.nnezh

import org.nnezh.lexer.readSource
import org.nnezh.org.nnezh.compiler.CTranspile
import org.nnezh.org.nnezh.compiler.TACCompilerImpl
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

private const val DEFAULT_SOURCE_PATH = "examples/3ac.bred"
private const val DEFAULT_C_OUTPUT_PATH = "main.c"
private const val DEFAULT_EXE_OUTPUT_PATH = "main.exe"
private const val DEFAULT_VCVARS64_PATH =
    "C:\\Program Files\\Microsoft Visual Studio\\18\\Community\\VC\\Auxiliary\\Build\\vcvars64.bat"

fun main(args: Array<String>) {
    val sourcePath = args.getOrNull(0) ?: DEFAULT_SOURCE_PATH
    val cOutputPath = args.getOrNull(1) ?: DEFAULT_C_OUTPUT_PATH
    val exeOutputPath = args.getOrNull(2) ?: DEFAULT_EXE_OUTPUT_PATH
    val vcvarsPath = System.getenv("VCVARS64_PATH") ?: DEFAULT_VCVARS64_PATH

    try {
        val source = readSource(sourcePath).fold(
            ifLeft = { error(it.message) },
            ifRight = { it },
        )
        val tac = TACCompilerImpl().compile(source)
        val cBody = CTranspile().compile(tac)
        val cFile = Path.of(cOutputPath).toAbsolutePath()
        val runtime = Files.readAllLines(Path.of("runtime.c"))

        cFile.parent?.let { Files.createDirectories(it) }
        Files.write(cFile, runtime + cBody)

        compileWithMsvc(
            vcvarsPath = vcvarsPath,
            cFile = cFile,
            exeFile = Path.of(exeOutputPath).toAbsolutePath(),
        )

        println("Generated ${cFile}")
        println("Generated ${Path.of(exeOutputPath).toAbsolutePath()}")
    } catch (error: Throwable) {
        System.err.println(error.message ?: error.toString())
        exitProcess(1)
    }
}

private fun compileWithMsvc(
    vcvarsPath: String,
    cFile: Path,
    exeFile: Path,
) {
    val vcvarsFile = File(vcvarsPath)
    if (!vcvarsFile.exists()) {
        error("MSVC vcvars64.bat not found: $vcvarsPath")
    }
    if (!Files.exists(cFile)) {
        error("Generated C file does not exist: $cFile")
    }

    exeFile.parent?.let { Files.createDirectories(it) }

    val command = listOf(
        "cmd.exe",
        "/c",
        "\"\"$vcvarsPath\" && cl.exe /O2 /Fe:\"${exeFile.fileName}\" \"${cFile.fileName}\"\"",
    )

    val process = ProcessBuilder(command)
        .directory(cFile.parent.toFile())
        .redirectErrorStream(true)
        .start()

    process.inputStream.bufferedReader().use { reader ->
        reader.forEachLine { line -> println("[MSVC]: $line") }
    }

    val exitCode = process.waitFor()
    if (exitCode != 0) {
        error("MSVC cl.exe failed with exit code $exitCode")
    }

    val producedExe = cFile.parent.resolve(exeFile.fileName)
    if (producedExe != exeFile && Files.exists(producedExe)) {
        Files.move(producedExe, exeFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
    if (!Files.exists(exeFile)) {
        error("MSVC cl.exe finished successfully but executable was not found: $exeFile")
    }
}
