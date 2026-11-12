package org.nnezh

import java.io.IOException
import kotlin.system.exitProcess

private const val DEFAULT_SOURCE_PATH = "examples/3ac.bred"
private const val DEFAULT_EXE_OUTPUT_PATH = "main.exe"
private const val GENERATED_EXECUTABLE_PREFIX = "Generated executable: "

fun main(args: Array<String>) {
    val sourcePath = args.getOrNull(0) ?: DEFAULT_SOURCE_PATH
    val exeOutputPath = args.getOrNull(1) ?: DEFAULT_EXE_OUTPUT_PATH
    val keepIntermediateC = args.getOrNull(2)?.toBooleanStrictOrNull() ?: true
    val standardLibraryProvider = StandardLibraryProvider()

    val cLines = BredToCCompilerFactory(standardLibraryProvider = standardLibraryProvider)
        .create()
        .compile(sourcePath)
        .fold(
            ifLeft = { errors ->
                errors.forEach { System.err.println(it) }
                exitProcess(1)
            },
            ifRight = { it },
        )

    val messages = CExecutableBuilder(runtimePath = standardLibraryProvider.runtimePath).build(
        cLines = cLines,
        outputExe = exeOutputPath,
        keepIntermediateC = true,
    )

    messages.forEach(::println)
    val executablePath = messages.lastOrNull()
        ?.takeIf { it.startsWith(GENERATED_EXECUTABLE_PREFIX) }
        ?.removePrefix(GENERATED_EXECUTABLE_PREFIX)
        ?: exitProcess(1)

    val process = try {
        ProcessBuilder(executablePath)
            .inheritIO()
            .start()
    } catch (error: IOException) {
        System.err.println("Could not start executable $executablePath: ${error.message}")
        exitProcess(1)
    }

    val exitCode = try {
        process.waitFor()
    } catch (error: InterruptedException) {
        process.destroy()
        Thread.currentThread().interrupt()
        System.err.println("Executable run was interrupted: ${error.message}")
        exitProcess(1)
    }
    exitProcess(exitCode)
}
