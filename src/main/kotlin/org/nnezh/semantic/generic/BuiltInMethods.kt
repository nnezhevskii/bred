package org.nnezh.org.nnezh.semantic.generic

import org.nnezh.org.nnezh.base.Type

object BuiltInMethods {
    val functions: List<FunctionSignature> = listOf(

        FunctionSignature(name = "readString", args = listOf(Type.StringType, Type.IntType), returnType = Type.UnitType),
        FunctionSignature(name = "println", args = listOf(Type.StringType), returnType = Type.UnitType),
        FunctionSignature(name = "stringToInt", args = listOf(Type.StringType), returnType = Type.IntType),
        FunctionSignature(name = "intToString", args = listOf(Type.IntType, Type.StringType, Type.IntType), returnType = Type.UnitType),
        FunctionSignature(name = "doubleToString", args = listOf(Type.DoubleType, Type.StringType, Type.IntType), returnType = Type.UnitType),
        FunctionSignature(name = "stringToDouble", args = listOf(Type.StringType), returnType = Type.DoubleType),
        FunctionSignature(name = "intToDouble", args = listOf(Type.IntType), returnType = Type.DoubleType),
        FunctionSignature(name = "readInt", args = listOf(), returnType = Type.IntType),
        FunctionSignature(name = "readDouble", args = listOf(), returnType = Type.DoubleType),
        FunctionSignature(name = "readBoolean", args = listOf(), returnType = Type.BoolType),
        FunctionSignature(name = "doubleToInt", args = listOf(Type.DoubleType), returnType = Type.IntType),
        FunctionSignature(name = "booleanToString", args = listOf(Type.BoolType, Type.StringType, Type.IntType), returnType = Type.UnitType),
        FunctionSignature(name = "stringLength", args = listOf(Type.StringType), returnType = Type.IntType),
        FunctionSignature(name = "stringConcat", args = listOf(Type.StringType, Type.StringType, Type.StringType, Type.IntType), returnType = Type.UnitType),
        FunctionSignature(name = "stringEquals", args = listOf(Type.StringType, Type.StringType), returnType = Type.BoolType),
        FunctionSignature(name = "substring", args = listOf(Type.StringType, Type.IntType, Type.IntType, Type.StringType, Type.IntType), returnType = Type.UnitType),
        FunctionSignature(name = "currentTimeMillis", args = listOf(), returnType = Type.IntType),
        FunctionSignature(name = "random", args = listOf(Type.IntType, Type.IntType), returnType = Type.IntType),
    )
}