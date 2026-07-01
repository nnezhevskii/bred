package org.nnezh.bred.common

object BuiltInMethods {

    val StringType = TypeSign("String")
    val BooleanType = TypeSign("Boolean")
    val DoubleType = TypeSign("Double")
    val IntType = TypeSign("Int")
    val UnitType = TypeSign("Unit")

    val functions: List<FunctionSignature> = listOf(

        FunctionSignature(name = "println", args = listOf(StringType), UnitType),
        FunctionSignature(name = "stringToInt", args = listOf(StringType), IntType),
        FunctionSignature(name = "doubleToString", args = listOf(DoubleType, StringType, IntType), UnitType),
        FunctionSignature(name = "stringToDouble", args = listOf(StringType), DoubleType),
        FunctionSignature(name = "intToDouble", args = listOf(IntType), DoubleType),
        FunctionSignature(name = "readInt", args = listOf(), IntType),
        FunctionSignature(name = "readDouble", args = listOf(), DoubleType),
        FunctionSignature(name = "readBoolean", args = listOf(), BooleanType),
        FunctionSignature(name = "doubleToInt", args = listOf(DoubleType), IntType),
        FunctionSignature(name = "booleanToString", args = listOf(BooleanType, StringType, IntType), UnitType),
        FunctionSignature(name = "random", args = listOf(IntType, IntType), IntType),

        FunctionSignature(name = "readString", args = listOf(), StringType),
        FunctionSignature(name = "intToString", args = listOf(IntType), StringType),
        FunctionSignature(name = "doubleToString", args = listOf(DoubleType), StringType),
        FunctionSignature(name = "random", args = listOf(IntType, IntType), IntType),


//        FunctionSignature(name = "stringLength", args = listOf(StringType), IntType),
//        FunctionSignature(name = "stringConcat", args = listOf(StringType, StringType, StringType, IntType), UnitType),
//        FunctionSignature(name = "intToString", args = listOf(IntType, StringType, IntType), UnitType),
//        FunctionSignature(name = "substring", args = listOf(StringType, IntType, IntType, StringType, IntType), UnitType),

        //        FunctionSignature(name = "stringEquals", args = listOf(StringType, StringType), BooleanType),
//        FunctionSignature(name = "currentTimeMillis", args = listOf(), IntType),
    )
}