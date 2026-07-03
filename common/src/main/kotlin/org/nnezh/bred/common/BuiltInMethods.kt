package org.nnezh.bred.common

object BuiltInMethods {
    data class BuiltInFunctionSpec(
        val name: String,
        val args: List<TypeRef>,
        val result: TypeRef,
    )

    private val string = TypeRef(TypeNames.STRING)
    private val boolean = TypeRef(TypeNames.BOOLEAN)
    private val double = TypeRef(TypeNames.DOUBLE)
    private val int = TypeRef(TypeNames.INT)
    private val unit = TypeRef(TypeNames.UNIT)

    val specs: List<BuiltInFunctionSpec> = listOf(
        BuiltInFunctionSpec(name = "println", args = listOf(string), unit),
        BuiltInFunctionSpec(name = "stringToInt", args = listOf(string), int),
        BuiltInFunctionSpec(name = "doubleToString", args = listOf(double, string, int), unit),
        BuiltInFunctionSpec(name = "stringToDouble", args = listOf(string), double),
        BuiltInFunctionSpec(name = "intToDouble", args = listOf(int), double),
        BuiltInFunctionSpec(name = "readInt", args = listOf(), int),
        BuiltInFunctionSpec(name = "readDouble", args = listOf(), double),
        BuiltInFunctionSpec(name = "readBoolean", args = listOf(), boolean),
        BuiltInFunctionSpec(name = "doubleToInt", args = listOf(double), int),
        BuiltInFunctionSpec(name = "booleanToString", args = listOf(boolean, string, int), unit),

        BuiltInFunctionSpec(name = "stringEquals", args = listOf(string, string), boolean),
        BuiltInFunctionSpec(name = "readString", args = listOf(), string),
        BuiltInFunctionSpec(name = "intToString", args = listOf(int), string),
        BuiltInFunctionSpec(name = "doubleToString", args = listOf(double), string),
        BuiltInFunctionSpec(name = "random", args = listOf(int, int), int),

//        BuiltInFunctionSpec(name = "stringLength", args = listOf(string), int),
//        BuiltInFunctionSpec(name = "stringConcat", args = listOf(string, string, string, int), unit),
//        BuiltInFunctionSpec(name = "intToString", args = listOf(int, string, int), unit),
//        BuiltInFunctionSpec(name = "substring", args = listOf(string, int, int, string, int), unit),
//        BuiltInFunctionSpec(name = "currentTimeMillis", args = listOf(), int),
    )

    fun signatures(resolve: (TypeRef) -> TypeSign): List<FunctionSignature> =
        specs.map { spec ->
            FunctionSignature(
                name = spec.name,
                args = spec.args.map(resolve),
                resultType = resolve(spec.result),
            )
        }
}
