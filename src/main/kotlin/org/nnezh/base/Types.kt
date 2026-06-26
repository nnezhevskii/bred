package org.nnezh.org.nnezh.base

import arrow.core.Either
import arrow.core.left
import arrow.core.right

sealed interface Type {
    val name: String

    object IntType : Type {
        override val name = "Int"
        override fun toString(): String = name
    }
    object StringType : Type {
        override val name = "String"
        override fun toString(): String = name
    }
    object DoubleType : Type {
        override val name = "Double"
        override fun toString(): String = name
    }
    object BoolType : Type {
        override val name = "Boolean"
        override fun toString(): String = name
    }
    object UnitType : Type {
        override val name = "Unit"
        override fun toString(): String = name
    }

    data class StaticArrayType(val elementType: Type, val count: Int): Type {
        override val name = "Array"
        override fun toString(): String = name

        override fun equals(other: Any?): Boolean {
            return if (other is StaticArrayType) {
                elementType == other.elementType
            } else {
                false
            }
        }

        override fun hashCode(): Int {
            var result = count
            result = 31 * result + elementType.hashCode()
            result = 31 * result + name.hashCode()
            return result
        }
    }

    companion object {
//        private val allTypes = listOf(IntType, StringType, DoubleType, BoolType, UnitType, StaticArrayType)
//
//        private val stringToTypeMap = allTypes.associateBy { it.name }

        // Left message is internal; user-facing text (with position) comes from AstErrorFactory.invalidType.
        fun fromString(name: String): Either<String, Type> {
            return when (name) {
                "Int" -> IntType.right()
                "String" -> StringType.right()
                "Double" -> DoubleType.right()
                "Boolean" -> BoolType.right()
                "Unit" -> UnitType.right()
                else -> ("Invalid type: $name").left()
            }
        }

        fun toString(type: Type): String = type.name
    }
}