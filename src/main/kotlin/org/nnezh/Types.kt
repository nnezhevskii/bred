package org.nnezh.org.nnezh
sealed interface Type {
    val name: String

    object IntType : Type { override val name = "Int" }
    object StringType : Type { override val name = "String" }
    object DoubleType : Type { override val name = "Double" }
    object BoolType : Type { override val name = "Boolean" }

    companion object {
        private val allTypes = listOf(IntType, StringType, DoubleType, BoolType)

        private val stringToTypeMap = allTypes.associateBy { it.name }


        fun fromString(name: String): Type {
            return stringToTypeMap[name] ?: throw IllegalArgumentException("Invalid type: $name")
        }

        fun parseOrNull(name: String): Type? = stringToTypeMap[name]

        fun toString(type: Type): String = type.name
    }
}