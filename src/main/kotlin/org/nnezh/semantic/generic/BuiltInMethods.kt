package org.nnezh.org.nnezh.semantic.generic

import org.nnezh.org.nnezh.base.Type

object BuiltInMethods {
    val functions: List<FunctionSignature> = listOf(

// 1. Было readString() -> String, стало: принимает буфер и его размер, возвращает Unit
        FunctionSignature(name = "readString", args = listOf(Type.StringType, Type.IntType), returnType = Type.UnitType),

// 2. Тут всё без изменений, строка просто читается
        FunctionSignature(name = "println", args = listOf(Type.StringType), returnType = Type.UnitType),

// 3. Тут тоже без изменений, строка на входе, инт на выходе (куча не нужна)
        FunctionSignature(name = "stringToInt", args = listOf(Type.StringType), returnType = Type.IntType),

// 4. Было intToString(Int) -> String, стало: принимает инт, буфер-приёмник, размер буфера -> Unit
        FunctionSignature(name = "intToString", args = listOf(Type.IntType, Type.StringType, Type.IntType), returnType = Type.UnitType),

// 5. Было doubleToString(Double) -> String, аналогично едем на стек
        FunctionSignature(name = "doubleToString", args = listOf(Type.DoubleType, Type.StringType, Type.IntType), returnType = Type.UnitType),

// 6. Без изменений
        FunctionSignature(name = "stringToDouble", args = listOf(Type.StringType), returnType = Type.DoubleType),

// 7. Без изменений
        FunctionSignature(name = "intToDouble", args = listOf(Type.IntType), returnType = Type.DoubleType),

// 8. Без изменений
        FunctionSignature(name = "readInt", args = listOf(), returnType = Type.IntType),

// 9. Без изменений
        FunctionSignature(name = "readDouble", args = listOf(), returnType = Type.DoubleType),

// 10. Без изменений
        FunctionSignature(name = "readBoolean", args = listOf(), returnType = Type.BoolType),

// 11. Без изменений
        FunctionSignature(name = "doubleToInt", args = listOf(Type.DoubleType), returnType = Type.IntType),

// 12. Было booleanToString(Bool) -> String, теперь пишет в буфер
        FunctionSignature(name = "booleanToString", args = listOf(Type.BoolType, Type.StringType, Type.IntType), returnType = Type.UnitType),

// 13. Без изменений
        FunctionSignature(name = "stringLength", args = listOf(Type.StringType), returnType = Type.IntType),

// 14. Было stringConcat(String, String) -> String, теперь принимает два исходника, буфер для результата и его размер
        FunctionSignature(name = "stringConcat", args = listOf(Type.StringType, Type.StringType, Type.StringType, Type.IntType), returnType = Type.UnitType),

// 15. Без изменений, просто сравниваем две готовые строки
        FunctionSignature(name = "stringEquals", args = listOf(Type.StringType, Type.StringType), returnType = Type.BoolType),

// 16. Было substring(String, Int, Int) -> String, теперь в конце добавляются буфер-назначение и размер
        FunctionSignature(name = "substring", args = listOf(Type.StringType, Type.IntType, Type.IntType, Type.StringType, Type.IntType), returnType = Type.UnitType),

// 17. Без изменений
        FunctionSignature(name = "currentTimeMillis", args = listOf(), returnType = Type.IntType)
    )
}