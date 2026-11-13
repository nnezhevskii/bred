package org.nnezh.root

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.nnezh.BredToCCompilerFactory
import org.nnezh.CExecutableBuilder
import org.nnezh.DEFAULT_VCVARS64_PATH
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

class ProductFieldExecutionTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `writing a shadowed counter leaves the outer counter intact`() {
        assertOutput(
            """
            type mut Counter(value: Int)

            fun main(): Int {
                val item: Counter = Counter(7)
                if (true) {
                    val item: Counter = Counter(40)
                    item.value = item.value + 2
                    printInt(item.value)
                }
                printInt(item.value)
                return 0
            }
            """,
            listOf("42", "7"),
        )
    }

    @Test
    fun `writing a nested field preserves its sibling with the same field name`() {
        assertOutput(
            """
            type mut Cell(value: Int)
            type PairOfCells(left: Cell, right: Cell)

            fun main(): Int {
                val pair: PairOfCells = PairOfCells(Cell(3), Cell(11))
                pair.left.value = pair.right.value + 2
                printInt(pair.left.value)
                printInt(pair.right.value)
                return 0
            }
            """,
            listOf("13", "11"),
        )
    }

    @Test
    fun `when branches read the selected payload even with identical binding names`() {
        assertOutput(
            """
            type Cell(value: Int)
            type Choice = First(cell: Cell) | Second(cell: Cell)

            fun report(choice: Choice): Unit {
                when (choice) {
                    First(item) -> { printInt(item.value + 100) }
                    Second(item) -> { printInt(item.value + 200) }
                }
            }

            fun main(): Int {
                report(Second(Cell(7)))
                report(First(Cell(3)))
                return 0
            }
            """,
            listOf("207", "103"),
        )
    }

    private fun assertOutput(program: String, expected: List<String>) {
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
            assertEquals(0, process.exitValue(), lines.joinToString("\n"))
            assertEquals(expected, lines)
        } finally {
            if (process.isAlive) process.destroyForcibly().waitFor()
        }
    }
}
