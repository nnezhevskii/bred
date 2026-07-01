package org.nnezh.org.nnezh.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.nnezh.org.nnezh.ICGenerator.LLTACElement
import org.nnezh.org.nnezh.base.Type

class CTranspileTest {

    @Test
    fun `transpiles minimal main function`() {
        val tac = listOf(
            LLTACElement.function("main", Type.UnitType),
            LLTACElement.ret(),
        )

        assertEquals(
            listOf(
                "int main(int size, char** arg) {",
                "return;",
                "}",
            ),
            CTranspile().compile(tac),
        )
    }
}
