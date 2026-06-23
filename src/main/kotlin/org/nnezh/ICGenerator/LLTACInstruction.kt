package org.nnezh.org.nnezh.ICGenerator

sealed interface LLTACElement {
    companion object {
        fun function(name: String): LLTACElement {
            return LLTACLabel(name)
        }
    }
}

data class LLTACInstruction(
    val opcode: LLTACOperation,
    val destination: Operand?,
    val arg1: Operand?,
    val arg2: Operand?
): LLTACElement {
    override fun toString(): String {
        return "$opcode ${destination?.name} ${arg1 ?: ""} ${arg2 ?: ""}".trimIndent()
    }

}

data class LLTACLabel(val name: String): LLTACElement {
    override fun toString() = "func $name:"
}