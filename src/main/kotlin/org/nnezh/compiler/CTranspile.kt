package org.nnezh.org.nnezh.compiler

import arrow.core.raise.context.ensure
import org.nnezh.org.nnezh.ICGenerator.LLTACElement
import org.nnezh.org.nnezh.ICGenerator.LLTACFunc
import org.nnezh.org.nnezh.ICGenerator.LLTACInstruction
import org.nnezh.org.nnezh.ICGenerator.LLTACLabel
import org.nnezh.org.nnezh.ICGenerator.LLTACOperation
import org.nnezh.org.nnezh.ICGenerator.LLTACRetInstruction
import org.nnezh.org.nnezh.ICGenerator.LeftValue
import org.nnezh.org.nnezh.ICGenerator.Operand
import org.nnezh.org.nnezh.ICGenerator.RightValue
import org.nnezh.org.nnezh.base.Type
import java.util.Collections.singletonList

class CTranspile(private val tacCompiler: TACCompiler = TACCompilerImpl()) {

    private val StringDefaultSize = 1024
    private var firstFunctionWasProceeded: Boolean = false

    fun compile(path: String): List<String> {
        return compile(tacCompiler.compile(path))
    }

    fun compile(instructions: List<LLTACElement>): List<String> {
        val context = Context(instructions)
        val cCode = mutableListOf<String>()
        while (!context.eof) {
            cCode.addAll(
                when (context.top) {
                    is LLTACFunc -> {
                        parseFunction(context)
                    }

                    is LLTACRetInstruction -> parseReturnInstruction(context)
                    is LLTACInstruction -> parseInstruction(context)
                    is LLTACLabel -> parseLabel(context)
                }
            )
        }
        cCode.add("}")
        return cCode
    }

    private fun parseLabel(context: Context): List<String> {
        val label: LLTACLabel = context.consume() as LLTACLabel
        return singletonList(
            "${label.name}:"
        )
    }

    private fun parseInstruction(context: Context): List<String> {
//        val element = context.consume()
        val cCode = mutableListOf<String>()

        val instruction = (context.consume() as LLTACInstruction)
        when (instruction.opcode) {
            LLTACOperation.LLTAC_ASSIGN -> {
                val dest = (instruction.destination as LeftValue).asLeftValue()
                val value = (instruction.arg1!! as RightValue).asRightValue()
                if (instruction.destination.type == Type.StringType) {
                    cCode.add("strncpy(${dest}, ${value}, sizeof(${dest}));")
                } else {
                    cCode.add(
                        "$dest = $value;"
                    )
                }

            }

            LLTACOperation.LLTAC_GET_PARAM -> { /* DO Nothing */
            }

            LLTACOperation.LLTAC_ALLOC -> TODO()
            LLTACOperation.LLTAC_PARAM -> { /* Do nothing */
            }

            LLTACOperation.LLTAC_ADD -> TODO()
            LLTACOperation.LLTAC_SUB -> TODO()
            LLTACOperation.LLTAC_MUL -> TODO()
            LLTACOperation.LLTAC_DIV -> TODO()
            LLTACOperation.LLTAC_MOD -> TODO()
            LLTACOperation.LLTAC_EQ -> TODO()
            LLTACOperation.LLTAC_NEQ -> TODO()
            LLTACOperation.LLTAC_LT -> TODO()
            LLTACOperation.LLTAC_GT -> TODO()
            LLTACOperation.LLTAC_LE -> TODO()
            LLTACOperation.LLTAC_GE -> TODO()
            LLTACOperation.LLTAC_AND -> TODO()
            LLTACOperation.LLTAC_OR -> TODO()
            LLTACOperation.LLTAC_CALL -> {
                val callingFunctionName = (instruction.arg1 as Operand.FunctionCall).name
                val destination = instruction.destination as? LeftValue
                val amountOfArgs: Int = (instruction.arg2 as Operand.IntConst).value.toInt()
                val paramInstructions = (1.until(amountOfArgs + 1))
                    .map { i ->
                        (((context.top(-i - 1) as LLTACInstruction).destination) as Operand.Variable).name
                    }
                val args: String = paramInstructions.reversed().joinToString(",")

                val callingFunctionStr = "$callingFunctionName(${args})"
                var finalLine = callingFunctionStr
                destination?.let {
                    finalLine = "${it.asLeftValue()} = $callingFunctionStr"
                }
                finalLine = "$finalLine;"

                cCode.add(finalLine)
            }

            LLTACOperation.LLTAC_RET -> TODO()
            LLTACOperation.LLTAC_NOT -> TODO()
            LLTACOperation.LLTAC_NEG -> TODO()
            LLTACOperation.LLTAC_JMP_IF_NOT -> {
                val destination = instruction.destination as Operand.Label
                val condition = (instruction.arg1 as LeftValue).asLeftValue()
                cCode.add("if (!${condition})goto ${destination};")
            }

            LLTACOperation.LLTAC_JMP -> {
                val destination = instruction.destination as Operand.Label
                cCode.add("goto ${destination};")
            }

            LLTACOperation.LLTAC_LDX -> TODO()
            LLTACOperation.LLTAC_STX -> TODO()
        }

        return cCode
    }

    private fun parseFunction(context: Context): List<String> {
        val cCode = mutableListOf<String>()
        if (firstFunctionWasProceeded) {
            cCode.add("}")
        }
        cCode.addAll(parseFunc(context))

        var pointer = 0
        val variables: MutableMap<Type, MutableSet<String>> = mutableMapOf()
        while (true) {
            val localTop = context.top(pointer)
            if (localTop == null || localTop is LLTACFunc) {
                break
            }
            if (localTop is LLTACInstruction &&
                (localTop.opcode == LLTACOperation.LLTAC_ASSIGN ||
                        localTop.opcode == LLTACOperation.LLTAC_CALL)
            ) {
                (localTop.destination as? Operand.Variable)?.let {
                    variables.computeIfAbsent(it.type) { mutableSetOf() }
                    variables[it.type]!!.add(it.name)
                }

            }

            pointer++
        }
        for (type in variables.keys) {
            if (type is Type.StaticArrayType) {

            } else if (type is Type.StringType) {
                val names = variables[type]!!.joinToString(", ") { "${it}[${StringDefaultSize}]" }
                val res = "char $names;"
                cCode.add(res)
            } else {
                cCode.add("${typeToStringInInitialization(type)} ${variables[type]!!.joinToString(", ")};")
            }

        }

        firstFunctionWasProceeded = true
        return cCode
    }


    private fun parseReturnInstruction(context: Context): List<String> {
        val top = context.consume() as LLTACRetInstruction

        val returnInstruction: String = top.value.fold(
            ifLeft = { "return;" },
            ifRight = { "return ${it.first};" }
        )

        return listOf(
            returnInstruction
        )
    }

    private fun isMainFunction(name: String): Boolean {
        return name == "main"
    }

    private fun parseFunc(context: Context): List<String> {
        val top = context.top as LLTACFunc
        val (name, type) = top.name to top.type
        context.consume()

        val params = mutableListOf<Pair<String, Type>>()
        while ((context.top as? LLTACInstruction)?.opcode == LLTACOperation.LLTAC_GET_PARAM) {
            val arg: Operand.Variable = (context.consume() as LLTACInstruction).destination as Operand.Variable
            params.add(arg.name to arg.type)
        }
        // TODO: добавить семантическую проверку, или воркэраунд для функции main
        if (isMainFunction(name)) {
            return listOf(
                "int main(int size, char** arg) {"
            )
        }

        val lines = mutableListOf<String>()
        val param = params.joinToString(",") { par -> "${typeToStringInArguments(par.second)} ${par.first}" }
        lines.add(
            "${typeToStringInArguments(type)} $name(${param}) {"
        )

        return lines
    }

//    private fun initializationLine(type: Type, names: List<String>): String {
//        return if (type is Type.StaticArrayType) {
//            names.map {  }
//        } else {
//            "${type} ${names.joinToString(", ")};"
//        }
//    }

    private fun typeToStringInInitialization(type: Type): String {
        return when (type) {
            Type.BoolType -> "bool"
            Type.DoubleType -> "double"
            Type.IntType -> "int"
            is Type.StaticArrayType -> {
                TODO()
            }

            Type.StringType -> "char[]"
            Type.UnitType -> "void"
        }
    }

    private fun typeToStringInArguments(type: Type): String {
        return when (type) {
            Type.BoolType -> "bool"
            Type.DoubleType -> "double"
            Type.IntType -> "int"
            is Type.StaticArrayType -> {
                if (type.elementType == Type.StringType) {
                    "const char**"
                } else {
                    "const ${typeToStringInArguments(type.elementType)}*"
                }
            }

            Type.StringType -> "const char*"
            Type.UnitType -> "void"
        }
    }

    private class Context(private val tacLines: List<LLTACElement>) {
        private var counter: Int = 0

        val eof: Boolean
            get() = counter == tacLines.size

        val top: LLTACElement
            get() {
                return tacLines[counter]
            }

        fun top(offset: Int): LLTACElement? {
            if (counter + offset < 0 || counter + offset >= tacLines.size) {
                return null
            }
            return tacLines[counter + offset]
        }

        fun consume(): LLTACElement {
            return tacLines[counter++]
        }
    }
}