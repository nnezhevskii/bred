package org.nnezh.lltag

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import org.junit.jupiter.api.Test
import org.nnezh.lexer.Lexer
import org.nnezh.org.nnezh.ICGenerator.PrettyPrinter
import org.nnezh.org.nnezh.compiler.TACCompiler
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class LLTAGSnapshotTester {

    @Test
    fun testFactorial() {
        listOf("factorial", "for", "sortThree", "isEven", "complexShock").forEach {
            compare(it)
        }

    }

    fun compare(testName: String) {
        val tacCodePath = Paths.get("src/test/resources/$testName.3ac")
        val sourceCode: Path = Paths.get("src/test/resources/$testName.bred")
        val src = Files.readString(sourceCode)
        val actualCommands = TACCompiler().compile(src)
            .let { PrettyPrinter().format(it) }
            .map { it.replace(" ", "") }
        val expectedCommands = Files
            .readAllLines(tacCodePath)
            .map { it.replace(" ", "") }

        val patch = DiffUtils.diff(expectedCommands, actualCommands)

        // 2. Если дельт нет — расходимся, код совпал
        if (patch.deltas.isEmpty()) {
            return
        }

        // 3. Генерируем классический unified diff (3 строки контекста вокруг изменений)
        val unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
            "${testName}.3ac",
            "${testName}.bred",
            expectedCommands,
            patch,
            1
        )

        // 4. Склеиваем всё в одну красивую строку
        val diffOutput = unifiedDiff.joinToString(separator = "\n")

        // 5. Роняем тест с понятным логом
        throw AssertionError(
            """
            |
            |❌ 3AC SNAPSHOT MISMATCH FOR TEST: $testName
            |--------------------------------------------------
            |$diffOutput
            |--------------------------------------------------
            """.trimMargin()
        )
    }
}