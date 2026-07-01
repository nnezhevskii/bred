package org.nnezh

import kotlin.system.exitProcess

private const val DEFAULT_SOURCE_PATH = "examples/3ac.bred"
private const val DEFAULT_EXE_OUTPUT_PATH = "main.exe"

fun main(args: Array<String>) {
    val sourcePath = args.getOrNull(0) ?: DEFAULT_SOURCE_PATH
    val exeOutputPath = args.getOrNull(1) ?: DEFAULT_EXE_OUTPUT_PATH
    val keepIntermediateC = args.getOrNull(2)?.toBooleanStrictOrNull() ?: false

    val cLines = BredToCCompilerFactory()
        .create()
        .compile(sourcePath)
        .fold(
            ifLeft = { errors ->
                errors.forEach { System.err.println(it) }
                exitProcess(1)
            },
            ifRight = { it },
        )

    val messages = CExecutableBuilder().build(
        cLines = cLines,
        outputExe = exeOutputPath,
        keepIntermediateC = keepIntermediateC,
    )

    messages.forEach(::println)
    val hasError = messages.any { message ->
        message.contains("error", ignoreCase = true) ||
            message.contains("failed", ignoreCase = true) ||
            message.contains("not found", ignoreCase = true)
    }
    if (hasError) {
        exitProcess(1)
    }
}
