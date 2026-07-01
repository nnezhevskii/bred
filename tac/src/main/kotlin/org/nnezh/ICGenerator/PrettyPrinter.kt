package org.nnezh.org.nnezh.ICGenerator

class PrettyPrinter {
    fun format(list: List<LLTACElement>): List<String> {
        val commands = mutableListOf<String>()
        list.forEach { element ->
            if (element is LLTACLabel || element is LLTACFunc) {
                commands.add("$element:")
            } else {
                commands.add("  $element")
            }
        }
        return commands
    }

}