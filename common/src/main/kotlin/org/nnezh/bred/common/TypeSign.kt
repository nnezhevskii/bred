package org.nnezh.bred.common

data class TypeSign(
    public val name: String,
    public val args: List<TypeSign> = emptyList(),
)