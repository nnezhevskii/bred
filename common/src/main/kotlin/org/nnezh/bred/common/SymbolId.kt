package org.nnezh.bred.common

sealed interface SymbolId {
    val debugName: String

    data class GlobalVar(val id: Int, override val debugName: String) : SymbolId {
        override fun toString(): String = "global_${debugName}_$id"
    }
    data class LocalVar(val id: Int, override val debugName: String) : SymbolId {
        override fun toString(): String = "local_${debugName}_$id"
    }
    data class Param(val id: Int, override val debugName: String) : SymbolId {
        override fun toString(): String = "param_${debugName}_$id"
    }
    data class Function(val id: Int, override val debugName: String, val isExtern: Boolean = false) : SymbolId {
        override fun toString(): String = "function_${debugName}_$id"
    }
    data class Constructor(val typeName: String, val variantName: String, val tagIndex: Int) : SymbolId {
        override fun toString(): String = debugName
        override val debugName: String
            get() = typeName
    }
}

class SymbolIdGenerator {
    private var counter = 0
    fun nextGlobal(name: String) = SymbolId.GlobalVar(++counter, name)
    fun nextLocal(name: String) = SymbolId.LocalVar(++counter, name)
    fun nextParam(name: String) = SymbolId.Param(++counter, name)
    fun nextFunction(name: String, isExtern: Boolean = false) = SymbolId.Function(++counter, name, isExtern)
}