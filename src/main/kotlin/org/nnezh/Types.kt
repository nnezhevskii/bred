package org.nnezh.org.nnezh

import arrow.core.Either
import arrow.core.left
import arrow.core.right

sealed interface Type {
    val name: String

    object IntType : Type { override val name = "Int" }
    object StringType : Type { override val name = "String" }
    object DoubleType : Type { override val name = "Double" }
    object BoolType : Type { override val name = "Boolean" }
    object UnitType : Type { override val name = "Unit" }

    companion object {
        private val allTypes = listOf(IntType, StringType, DoubleType, BoolType, UnitType)

        private val stringToTypeMap = allTypes.associateBy { it.name }

        // Left message is internal; user-facing text (with position) comes from AstErrorFactory.invalidType.
        fun fromString(name: String): Either<String, Type> {
            return stringToTypeMap[name]?.right() ?: "Invalid type: $name".left()
        }

        fun toString(type: Type): String = type.name
    }
}