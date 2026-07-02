package org.nnezh.lltag

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.nnezh.org.nnezh.ICGenerator.PrettyPrinter
import org.nnezh.org.nnezh.compiler.TACCompilerImpl
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Execution(ExecutionMode.SAME_THREAD)
class LLTAGSnapshotTester {

    private val allPrograms: List<String> = listOf(
        "factorial",
        "for",
        "minMax",
        "isPositive",
        "arrayBubble",
        "binarySearch",
//        "sortThree",
//        "isEven",
//        "complexShock",
//
//        "minValue",
//        "random_case_failed_for_no_reason",
//
//
//        "nestedArraySearch",
//        "zeroTripFor",
//        "stringifyRow",
//        "nestedControl",
//        "arrayRotate",
//        "earlyReturnArray",
//        "whileCountdown",
//        "whileZeroTrip",
//        "whileEarlyReturn",
//        "whileGuardedAccum",
    )

    @TestFactory
    fun testFiles(): List<DynamicTest> =
        snapshotTests(allPrograms)

    private fun snapshotTests(programs: List<String>): List<DynamicTest> =
        programs.map { testName ->
            DynamicTest.dynamicTest("$testName.3ac matches $testName.bred") {
                compare(testName)
            }
        }

    fun compare(testName: String) {
        val tacCodePath = Paths.get("tac/src/test/resources/$testName.3ac")
        val sourceCode: Path = Paths.get("tac/src/test/resources/$testName.bred")
        val src = readSourceOrFail(testName, sourceCode, tacCodePath)
        val actualCommands = compileActualOrFail(testName, sourceCode, tacCodePath, src)
        val expectedCommands = readExpectedOrFail(testName, sourceCode, tacCodePath)

        val patch = DiffUtils.diff(expectedCommands, actualCommands)
        if (patch.deltas.isEmpty()) {
            return
        }

        val unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
            "${testName}.3ac",
            "${testName}.bred",
            expectedCommands,
            patch,
            1,
        )
        val diffOutput = unifiedDiff.joinToString(separator = "\n")

        throw AssertionError(
            """
            |
            |3AC snapshot mismatch for $testName
            |Expected snapshot: $tacCodePath
            |Source file:       $sourceCode
            |--------------------------------------------------
            |$diffOutput
            |--------------------------------------------------
            """.trimMargin()
        )
    }

    private fun readSourceOrFail(
        testName: String,
        sourceCode: Path,
        tacCodePath: Path,
    ): String =
        try {
            Files.readString(sourceCode)
        } catch (error: Throwable) {
            failSnapshot(testName, sourceCode, tacCodePath, "read source file", error)
        }

    private fun compileActualOrFail(
        testName: String,
        sourceCode: Path,
        tacCodePath: Path,
        src: String,
    ): List<String> =
        try {
            TACCompilerImpl().compile(src)
                .let { PrettyPrinter().format(it) }
                .map { it.replace(" ", "") }
        } catch (error: Throwable) {
            failSnapshot(testName, sourceCode, tacCodePath, "compile source to 3AC", error)
        }

    private fun readExpectedOrFail(
        testName: String,
        sourceCode: Path,
        tacCodePath: Path,
    ): List<String> =
        try {
            Files.readAllLines(tacCodePath)
                .map { it.replace(" ", "") }
        } catch (error: Throwable) {
            failSnapshot(testName, sourceCode, tacCodePath, "read expected snapshot", error)
        }

    private fun failSnapshot(
        testName: String,
        sourceCode: Path,
        tacCodePath: Path,
        phase: String,
        error: Throwable,
    ): Nothing {
        throw AssertionError(
            """
            |
            |3AC snapshot failure for $testName
            |Expected snapshot: $tacCodePath
            |Source file:       $sourceCode
            |Phase:             $phase
            |Cause:             ${error.message ?: error::class.qualifiedName}
            """.trimMargin(),
            error,
        )
    }
}
