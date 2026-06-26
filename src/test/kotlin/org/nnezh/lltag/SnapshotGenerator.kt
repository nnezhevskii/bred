package org.nnezh.lltag

import org.nnezh.org.nnezh.ICGenerator.PrettyPrinter
import org.nnezh.org.nnezh.compiler.TACCompilerImpl
import java.nio.file.Files
import java.nio.file.Paths

fun main() {
    listOf(
        "factorial",
        "for",
        "sortThree",
        "isEven",
        "complexShock",
        "isPositive",
        "minValue",
        "random_case_failed_for_no_reason",
        "minMax",
        "arrayBubble",
        "nestedArraySearch",
        "zeroTripFor",
        "stringifyRow",
        "nestedControl",
        "arrayRotate",
        "earlyReturnArray",
    ).forEach { testName ->
        val sourcePath = Paths.get("src/test/resources/$testName.bred")
        val outputPath = Paths.get("src/test/resources/$testName.3ac")
        val src = Files.readString(sourcePath)
        val actual = TACCompilerImpl()
            .compile(src)
            .let { PrettyPrinter().format(it) }
            .joinToString("\n")
        Files.writeString(outputPath, actual)
        println("Generated $outputPath")
    }
}
