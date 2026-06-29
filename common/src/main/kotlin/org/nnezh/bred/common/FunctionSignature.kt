package org.nnezh.bred.common

data class FunctionSignature(
    val name: String,
    val args: List<TypeSign>,
    val resultType: TypeSign
)