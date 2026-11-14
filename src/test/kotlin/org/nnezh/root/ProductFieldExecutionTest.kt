package org.nnezh.root

import org.junit.jupiter.api.Test

class ProductFieldExecutionTest : NativeProgramTestSupport() {
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
}
