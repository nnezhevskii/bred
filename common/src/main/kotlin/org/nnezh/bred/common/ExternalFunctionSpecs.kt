package org.nnezh.bred.common

object ExternalFunctionSpecs {
    data class ExternalFunctionSpec(
        val name: String,
        val args: List<TypeRef>,
        val result: TypeRef,
    )

    private val string = TypeRef(TypeNames.STRING)
    private val boolean = TypeRef(TypeNames.BOOLEAN)
    private val double = TypeRef(TypeNames.DOUBLE)
    private val int = TypeRef(TypeNames.INT)
    private val long = TypeRef(TypeNames.LONG)
    private val unit = TypeRef(TypeNames.UNIT)

    val specs: List<ExternalFunctionSpec> = listOf(
        ExternalFunctionSpec(name = "println", args = listOf(string), unit),
        ExternalFunctionSpec(name = "stringToInt", args = listOf(string), int),
        ExternalFunctionSpec(name = "doubleToString", args = listOf(double, string, int), unit),
        ExternalFunctionSpec(name = "stringToDouble", args = listOf(string), double),
        ExternalFunctionSpec(name = "intToDouble", args = listOf(int), double),
        ExternalFunctionSpec(name = "readInt", args = listOf(), int),
        ExternalFunctionSpec(name = "readDouble", args = listOf(), double),
        ExternalFunctionSpec(name = "readBoolean", args = listOf(), boolean),
        ExternalFunctionSpec(name = "doubleToInt", args = listOf(double), int),
        ExternalFunctionSpec(name = "booleanToString", args = listOf(boolean, string, int), unit),
        ExternalFunctionSpec(name = "stringEquals", args = listOf(string, string), boolean),
        ExternalFunctionSpec(name = "readString", args = listOf(), string),
        ExternalFunctionSpec(name = "intToString", args = listOf(int), string),
        ExternalFunctionSpec(name = "longToString", args = listOf(long), string),
        ExternalFunctionSpec(name = "doubleToString", args = listOf(double), string),
        ExternalFunctionSpec(name = "random", args = listOf(int, int), int),
        ExternalFunctionSpec(name = "currentTimeMillis", args = listOf(), long),
    )

    fun signatures(
        specs: List<ExternalFunctionSpec> = this.specs,
        resolve: (TypeRef) -> TypeSign,
    ): List<FunctionSignature> =
        specs.map { spec ->
            FunctionSignature(
                name = spec.name,
                args = spec.args.map(resolve),
                resultType = resolve(spec.result),
            )
        }
}
