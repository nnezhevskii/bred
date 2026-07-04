package org.nnezh.org.nnezh.compiler

import org.nnezh.org.nnezh.ICGenerator.LLTACElement
import org.nnezh.org.nnezh.ICGenerator.LLTACFunc
import org.nnezh.org.nnezh.ICGenerator.LLTACGlobalStaticArray
import org.nnezh.org.nnezh.ICGenerator.LLTACInstruction
import org.nnezh.org.nnezh.ICGenerator.LLTACLabel
import org.nnezh.org.nnezh.ICGenerator.LLTACOperation
import org.nnezh.org.nnezh.ICGenerator.LLTACRetInstruction
import org.nnezh.org.nnezh.ICGenerator.LeftValue
import org.nnezh.org.nnezh.ICGenerator.Operand
import org.nnezh.org.nnezh.ICGenerator.RightValue
import org.nnezh.bred.common.TypeNames
import org.nnezh.bred.common.TypeSign
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
        val cCode = collectGlobalStaticArrays(instructions)
            .map { renderGlobalStaticArray(it) }
            .toMutableList()
        cCode.addAll(collectFunctionHeaders(instructions)
            .map { renderPrototype(it) }
        )
        while (!context.eof) {
            cCode.addAll(
                when (context.top) {
                    is LLTACFunc -> {
                        parseFunction(context)
                    }

                    is LLTACGlobalStaticArray -> skipGlobalStaticArray(context)
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
                val params = mutableListOf<Pair<String, TypeSign>>()
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

    private fun collectGlobalStaticArrays(instructions: List<LLTACElement>): List<LLTACGlobalStaticArray> =
        instructions.filterIsInstance<LLTACGlobalStaticArray>()

    private fun skipGlobalStaticArray(context: Context): List<String> {
        context.consume()
        return emptyList()
    }

    private fun renderGlobalStaticArray(array: LLTACGlobalStaticArray): String {
        val elementTypeSign = array.type.elementTypeOrNull
            ?: throw CTranspileException("global array must have an element type: ${array.type.render()}")
        val elementType = typeToStringInInitialization(elementTypeSign)
        val initializer = array.initialValues
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = " = {", postfix = "}") { renderGlobalInitializer(it, elementTypeSign) }
            .orEmpty()

        return "$elementType ${array.name}[${array.size}]$initializer;"
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

        val instructionIndex = context.position
        val instruction = (context.consume() as LLTACInstruction)
        try {
        when (instruction.opcode) {
            LLTACOperation.LLTAC_ASSIGN -> {
                val destination = instruction.expectVariable(instruction.destination, "destination")
                val dest = instruction.expectLeftValue(instruction.destination, "destination").asLeftValue()
                val value = instruction.expectRightValue(instruction.arg1, "arg1").asRightValue()


                if (destination.type.isStringType()) {
                    val escaped = escapeCString(value)
                    cCode.add("$dest = create_string(\"$escaped\", strlen(\"$escaped\"));")
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
                val callingFunctionName = instruction.expectFunctionCall(instruction.arg1, "arg1").name
                val destination = instruction.destination as? LeftValue
                val amountOfArgs: Int = instruction.expectIntConst(instruction.arg2, "arg2").value.toInt()
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
                val left = instruction.expectLeftValue(instruction.destination, "destination").asLeftValue()
                val arg1 = instruction.expectLeftValue(instruction.arg1, "arg1").asLeftValue()
                cCode.add("${left}=!${arg1};")
            }
            LLTACOperation.LLTAC_NEG -> {
                val left = instruction.expectLeftValue(instruction.destination, "destination").asLeftValue()
                val arg1 = instruction.expectLeftValue(instruction.arg1, "arg1").asLeftValue()
                cCode.add("${left}=-${arg1};")
            }
            LLTACOperation.LLTAC_JMP_IF_NOT -> {
                val destination = instruction.expectLabel(instruction.destination, "destination")
                val condition = instruction.expectLeftValue(instruction.arg1, "arg1").asLeftValue()
                cCode.add("if (!${condition})goto ${destination};")
            }

            LLTACOperation.LLTAC_JMP -> {
                val destination = instruction.expectLabel(instruction.destination, "destination")
                cCode.add("goto ${destination};")
            }

            // TODO: not support arrays of arrays for a while
            LLTACOperation.LLTAC_ALLOC -> { /* Do nothing */ }

            LLTACOperation.LLTAC_LDX -> {
                val left = instruction.expectLeftValue(instruction.destination, "destination").asLeftValue()
                val index = instruction.expectRightValue(instruction.arg1, "arg1").asRightValue()
                val value = instruction.expectRightValue(instruction.arg2, "arg2").asRightValue()
                cCode.add("${left}=${index}[$value];")
            }
            LLTACOperation.LLTAC_STX -> {
                val left = instruction.expectLeftValue(instruction.destination, "destination").asLeftValue()
                val index = instruction.expectRightValue(instruction.arg1, "arg1").asRightValue()
                val value = instruction.expectRightValue(instruction.arg2, "arg2").asRightValue()
                cCode.add("${left}[$index]=${value};")

            }

            LLTACOperation.LLTAC_LDF -> TODO()
            LLTACOperation.LLTAC_STF -> TODO()
            LLTACOperation.LLTAC_STRUCT_DEF -> TODO()
            LLTACOperation.LLTAC_STRUCT_DEF_END -> TODO()
            LLTACOperation.LLTAC_FIELD -> TODO()
        }
        } catch (error: CTranspileException) {
            throw CTranspileException(
                "Malformed TAC at instruction #$instructionIndex (${instruction.opcode}): $instruction. ${error.message}"
            )
        } catch (error: Throwable) {
            throw CTranspileException(
                "Malformed TAC at instruction #$instructionIndex (${instruction.opcode}): $instruction. ${error.message ?: error::class.qualifiedName}"
            )
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
        val scalarVariables: MutableMap<TypeSign, MutableSet<String>> = mutableMapOf()
        val staticArrays: MutableMap<TypeSign, MutableSet<Pair<Operand.Variable, Int>>> = mutableMapOf()
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
                        val arrayType = it.type
                        val arraySize = localTop.expectIntConst(localTop.arg2, "arg2").value.toInt()
                        val elementType = arrayType.elementTypeOrNull
                            ?: throw CTranspileException("ALLOC destination must be array type, got ${arrayType.render()}")
                        staticArrays.computeIfAbsent(elementType) { mutableSetOf() }
                        staticArrays[elementType]!!.add(it to arraySize)
                    }
                } else {
                    if (localTop.opcode != LLTACOperation.LLTAC_STX) {
                        destination.let {
                            if (!it.type.isArray) {
                                scalarVariables.computeIfAbsent(it.type) { mutableSetOf() }
                                scalarVariables[it.type]!!.add(it.name)
                            }
                        }
                    }
                }
            }
        }

        for (type in staticArrays.keys) {
                val names = staticArrays[type]!!.map { (variable, size) -> "${variable.name}[$size]" }
                    .joinToString(", ")
                cCode.add("${typeToStringInInitialization(type)} $names;")
        }

        for (type in scalarVariables.keys) {
            cCode.add("${typeToStringInInitialization(type)} ${scalarVariables[type]!!.joinToString(", ")};")
        }

        firstFunctionWasProceeded = true
        return cCode
    }


    private fun parseReturnInstruction(context: Context): List<String> {
        val top = context.consume() as LLTACRetInstruction

        val returnInstruction: String = top.value?.let { "return ${it.first};" } ?: "return;"

        return listOf(
            returnInstruction
        )
    }

    private fun generateSimpleOperation(instruction: LLTACInstruction): String {

        val left = instruction.expectLeftValue(instruction.destination, "destination").asLeftValue()
        val arg1 = instruction.expectLeftValue(instruction.arg1, "arg1").asLeftValue()
        val arg2 = instruction.expectLeftValue(instruction.arg2, "arg2").asLeftValue()

        val destination = instruction.expectVariable(instruction.destination, "destination")
        if (destination.type.isStringType() && instruction.opcode == LLTACOperation.LLTAC_ADD) {
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

            else -> throw CTranspileException("Unsupported simple TAC opcode ${instruction.opcode} in instruction: $instruction")
        }

        return "${left}=${arg1}${command}${arg2};"
    }

    private fun LLTACInstruction.expectLeftValue(operand: Operand?, role: String): LeftValue =
        operand as? LeftValue
            ?: throw CTranspileException("expected $role to be a left-value operand, got ${operand.describe()}")

    private fun LLTACInstruction.expectRightValue(operand: Operand?, role: String): RightValue =
        operand as? RightValue
            ?: throw CTranspileException("expected $role to be a right-value operand, got ${operand.describe()}")

    private fun LLTACInstruction.expectFunctionCall(operand: Operand?, role: String): Operand.FunctionCall =
        operand as? Operand.FunctionCall
            ?: throw CTranspileException("expected $role to be a function call operand, got ${operand.describe()}")

    private fun LLTACInstruction.expectIntConst(operand: Operand?, role: String): Operand.IntConst =
        operand as? Operand.IntConst
            ?: throw CTranspileException("expected $role to be an Int constant operand, got ${operand.describe()}")

    private fun LLTACInstruction.expectLabel(operand: Operand?, role: String): Operand.Label =
        operand as? Operand.Label
            ?: throw CTranspileException("expected $role to be a label operand, got ${operand.describe()}")

    private fun Operand?.describe(): String =
        this?.let { "${it::class.simpleName}($it)" } ?: "<missing>"

    private fun LLTACInstruction.expectVariable(operand: Operand?, role: String): Operand.Variable =
        operand as? Operand.Variable
            ?: throw CTranspileException("expected $role to be a variable operand, got ${operand.describe()}")

    private fun renderGlobalInitializer(operand: Operand, elementType: TypeSign): String =
        if (elementType.isStringType() && operand is Operand.StringConst) {
            val escaped = escapeCString(operand.value)
            """{"$escaped", ${operand.value.length}}"""
        } else {
            (operand as RightValue).asRightValue()
        }

    private fun escapeCString(value: String): String =
        buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }

    private fun isMainFunction(name: String): Boolean {
        return name == "main"
    }

    private fun parseFunc(context: Context): List<String> {
        val top = context.top as LLTACFunc
        val (name, type) = top.name to top.type
        context.consume()

        val params = mutableListOf<Pair<String, TypeSign>>()
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


    private fun typeToStringInInitialization(type: TypeSign): String {
        return when {
            type.isBooleanType() -> "bool"
            type.isDoubleType() -> "double"
            type.isLongType() -> "long long"
            type.isIntType() -> "int"
            type.isArray -> throw CTranspileException("Cannot render static array type as scalar C declaration: ${type.render()}")
            type.isStringType() -> "String"
            type.isUnitType() -> "void"
            else -> throw CTranspileException("Unsupported C initialization type: ${type.render()}")
        }
    }

    private fun typeToStringInArguments(type: TypeSign): String {
        return when {
            type.isBooleanType() -> "bool"
            type.isDoubleType() -> "double"
            type.isLongType() -> "long long"
            type.isIntType() -> "int"
            type.isArray -> "${typeToStringInArguments(type.elementTypeOrNull!!)}*"
            type.isStringType() -> "String"
            type.isUnitType() -> "void"
            else -> throw CTranspileException("Unsupported C argument type: ${type.render()}")
        }
    }

    private fun TypeSign.isIntType(): Boolean =
        isPrimitive && name == TypeNames.INT

    private fun TypeSign.isLongType(): Boolean =
        isPrimitive && name == TypeNames.LONG

    private fun TypeSign.isDoubleType(): Boolean =
        isPrimitive && name == TypeNames.DOUBLE

    private fun TypeSign.isStringType(): Boolean =
        isPrimitive && name == TypeNames.STRING

    private fun TypeSign.isBooleanType(): Boolean =
        isPrimitive && name == TypeNames.BOOLEAN

    private fun TypeSign.isUnitType(): Boolean =
        isPrimitive && name == TypeNames.UNIT

    private data class FunctionHeader(
        val name: String,
        val returnType: TypeSign,
        val params: List<Pair<String, TypeSign>>,
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

        val position: Int
            get() = counter
    }
}
