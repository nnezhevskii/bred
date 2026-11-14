package org.nnezh.lltag

import org.nnezh.org.nnezh.ICGenerator.LLTACElement
import org.nnezh.org.nnezh.ICGenerator.LLTACFunc
import org.nnezh.org.nnezh.ICGenerator.LLTACGlobalStaticArray
import org.nnezh.org.nnezh.ICGenerator.LLTACInstruction
import org.nnezh.org.nnezh.ICGenerator.LLTACLabel
import org.nnezh.org.nnezh.ICGenerator.LLTACRetInstruction

class LLTACInterpreter {

    fun calculate(code: List<LLTACElement>) {
        for (line in code) {
            when (line) {
                is LLTACFunc -> TODO()
                is LLTACGlobalStaticArray -> TODO()
                is LLTACInstruction -> TODO()
                is LLTACLabel -> TODO()
                is LLTACRetInstruction -> TODO()
            }
        }

    }
}