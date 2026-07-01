package org.nnezh.org.nnezh.compiler

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

class CTranspile {

    private val runtime = listOf<String>(
        "#include <stdbool.h>",
        "#include <string.h>",
        "#include <stdio.h>"
    )

    private var firstFunctionWasProceeded: Boolean = false

    fun compile(instructions: List<LLTACElement>): List<String> {
        firstFunctionWasProceeded = false
        val context = Context(instructions)
        val cCode = collectFunctionHeaders(instructions)
            .map { renderPrototype(it) }
            .toMutableList()
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

    private fun collectFunctionHeaders(instructions: List<LLTACElement>): List<FunctionHeader> {
        val headers = mutableListOf<FunctionHeader>()
        var index = 0
        while (index < instructions.size) {
            val instruction = instructions[index]
            if (instruction is LLTACFunc) {
                val params = mutableListOf<Pair<String, Type>>()
                index++
                while (index < instructions.size) {
                    val param = instructions[index] as? LLTACInstruction ?: break
                    if (param.opcode != LLTACOperation.LLTAC_GET_PARAM) {
                        break
                    }
                    val variable = param.destination as Operand.Variable
                    params.add(variable.name to variable.type)
                    index++
                }
                headers.add(FunctionHeader(instruction.name, instruction.type, params))
            } else {
                index++
            }
        }
        return headers
    }

    private fun renderPrototype(header: FunctionHeader): String =
        "${renderFunctionSignature(header)};"

    private fun renderFunctionSignature(header: FunctionHeader): String =
        if (isMainFunction(header.name)) {
            "int main(int size, char** arg)"
        } else {
            val params = header.params.joinToString(",") { (name, type) -> "${typeToStringInArguments(type)} $name" }
            "${typeToStringInArguments(header.returnType)} ${header.name}($params)"
        }

    private fun parseLabel(context: Context): List<String> {
        val label: LLTACLabel = context.consume() as LLTACLabel
        return singletonList(
            "${label.name}:"
        )
    }

    private fun parseInstruction(context: Context): List<String> {
        val cCode = mutableListOf<String>()

        val instruction = (context.consume() as LLTACInstruction)
        when (instruction.opcode) {
            LLTACOperation.LLTAC_ASSIGN -> {
                val destination = instruction.destination as Operand
                val dest = (instruction.destination as LeftValue).asLeftValue()
                val value = (instruction.arg1!! as RightValue).asRightValue()


                if ((instruction.destination as Operand).type.name == "String") {
                    cCode.add("$dest = create_string(\"$value\", strlen(\"$value\"));")
                } else {
                    cCode.add("$dest = $value;")
                }

            }

            LLTACOperation.LLTAC_GET_PARAM -> { /* DO Nothing */ }


            LLTACOperation.LLTAC_PARAM -> { /* Do nothing */ }

            LLTACOperation.LLTAC_ADD, LLTACOperation.LLTAC_SUB, LLTACOperation.LLTAC_MUL, LLTACOperation.LLTAC_DIV,
            LLTACOperation.LLTAC_MOD, LLTACOperation.LLTAC_EQ, LLTACOperation.LLTAC_NEQ, LLTACOperation.LLTAC_LT,
            LLTACOperation.LLTAC_GT, LLTACOperation.LLTAC_LE, LLTACOperation.LLTAC_GE, LLTACOperation.LLTAC_AND,
            LLTACOperation.LLTAC_OR -> {
                cCode.add(generateSimpleOperation(instruction))
            }
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

            LLTACOperation.LLTAC_RET -> { /* Do nothing */ }
            LLTACOperation.LLTAC_NOT -> {
                val left = (instruction.destination as LeftValue).asLeftValue()
                val arg1 = (instruction.arg1 as LeftValue).asLeftValue()
                cCode.add("${left}!=${arg1};")
            }
            LLTACOperation.LLTAC_NEG -> {
                val left = (instruction.destination as LeftValue).asLeftValue()
                val arg1 = (instruction.arg1 as LeftValue).asLeftValue()
                cCode.add("${left}=-${arg1};")
            }
            LLTACOperation.LLTAC_JMP_IF_NOT -> {
                val destination = instruction.destination as Operand.Label
                val condition = (instruction.arg1 as LeftValue).asLeftValue()
                cCode.add("if (!${condition})goto ${destination};")
            }

            LLTACOperation.LLTAC_JMP -> {
                val destination = instruction.destination as Operand.Label
                cCode.add("goto ${destination};")
            }

            // TODO: not support arrays of arrays for a while
            LLTACOperation.LLTAC_ALLOC -> { /* Do nothing */ }

            LLTACOperation.LLTAC_LDX -> {
                val left = (instruction.destination as LeftValue).asLeftValue()
                val index = (instruction.arg1 as RightValue).asRightValue()
                val value = (instruction.arg2 as RightValue).asRightValue()
                cCode.add("${left}=${index}[$value];")
            }
            LLTACOperation.LLTAC_STX -> {
                val left = (instruction.destination as LeftValue).asLeftValue()
                val index = (instruction.arg1 as RightValue).asRightValue()
                val value = (instruction.arg2 as RightValue).asRightValue()
                cCode.add("${left}[$index]=${value};")

            }
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
        val scalarVariables: MutableMap<Type, MutableSet<String>> = mutableMapOf()
        val staticArrays: MutableMap<Type, MutableSet<Operand.Variable>> = mutableMapOf()
        while (true) {
            val localTop = context.top(pointer++)
            if (localTop == null || localTop is LLTACFunc) {
                break
            }
            val destination = (localTop as? LLTACInstruction)?.destination as? Operand.Variable
            if (localTop is LLTACInstruction && destination != null) {
                if (localTop.opcode == LLTACOperation.LLTAC_PARAM) {
                    continue
                }
                if (localTop.opcode == LLTACOperation.LLTAC_ALLOC) {
                    destination.let {
                        val arrayType = it.type as Type.StaticArrayType
                        staticArrays.computeIfAbsent(arrayType.elementType) { mutableSetOf() }
                        staticArrays[arrayType.elementType]!!.add(it)
                    }
                } else {
                    if (localTop.opcode != LLTACOperation.LLTAC_STX) {
                        destination.let {
                            if (it.type.name == "Array") {
//                                staticArrays.computeIfAbsent(it.type) { mutableSetOf() }
//                                staticArrays[it.type]!!.add(it)
                            } else {
                                scalarVariables.computeIfAbsent(it.type) { mutableSetOf() }
                                scalarVariables[it.type]!!.add(it.name)
                            }

                        }
                    }
                }
            }
        }

        for (type in staticArrays.keys) {
                val names = staticArrays[type]!!.map { variable -> "${variable.name}[${(variable.type as Type.StaticArrayType).count}]" }
                    .joinToString(", ")
                cCode.add("${typeToStringInInitialization((type as Type.StaticArrayType).elementType)} $names;")
        }

        for (type in scalarVariables.keys) {
            cCode.add("${typeToStringInInitialization(type)} ${scalarVariables[type]!!.joinToString(", ")};")
            // TODO
//            if (type is Type.StaticArrayType) {
//            } else if (type is Type.StringType) {
//                val names = scalarVariables[type]!!.joinToString(", ") { "${it}[${StringDefaultSize}]" }
//                val res = "char $names;"
//                cCode.add(res)
//            } else {
//
//            }
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

    private fun generateSimpleOperation(instruction: LLTACInstruction): String {

        val left = (instruction.destination as LeftValue).asLeftValue()
        val arg1 = (instruction.arg1 as LeftValue).asLeftValue()
        val arg2 = (instruction.arg2 as LeftValue).asLeftValue()

        if (instruction.destination!!.type.name == "String" && instruction.opcode == LLTACOperation.LLTAC_ADD) {
            return "${left}=concat(${arg1},${arg2});"
        }

        val command: String = when (instruction.opcode) {
            LLTACOperation.LLTAC_ADD -> "+"
            LLTACOperation.LLTAC_SUB -> "-"
            LLTACOperation.LLTAC_MUL -> "*"
            LLTACOperation.LLTAC_DIV -> "/"
            LLTACOperation.LLTAC_MOD -> "%"
            LLTACOperation.LLTAC_EQ -> "=="
            LLTACOperation.LLTAC_NEQ -> "!="
            LLTACOperation.LLTAC_LT -> "<"
            LLTACOperation.LLTAC_GT -> ">"
            LLTACOperation.LLTAC_LE -> "<="
            LLTACOperation.LLTAC_GE -> ">="
            LLTACOperation.LLTAC_AND -> "&&"
            LLTACOperation.LLTAC_OR  -> "||"

            else -> TODO()
        }

        return "${left}=${arg1}${command}${arg2};"
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
                "int main(int size, char** arg) {", "init_runtime();"

            )
        }

        return listOf("${renderFunctionSignature(FunctionHeader(name, type, params))} {")
    }


    private fun typeToStringInInitialization(type: Type): String {
        return when (type) {
            Type.BoolType -> "bool"
            Type.DoubleType -> "double"
            Type.IntType -> "int"
            is Type.StaticArrayType -> {
                TODO() // should not be there
            }

            Type.StringType -> "String"
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
                    "${typeToStringInArguments(type.elementType)}*"
                } else {
                    "${typeToStringInArguments(type.elementType)}*"
                }
            }

            Type.StringType -> "String"
            Type.UnitType -> "void"
        }
    }

    private data class FunctionHeader(
        val name: String,
        val returnType: Type,
        val params: List<Pair<String, Type>>,
    )

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
