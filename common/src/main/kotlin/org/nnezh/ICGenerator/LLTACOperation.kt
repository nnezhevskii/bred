package org.nnezh.org.nnezh.ICGenerator

enum class LLTACOperation {
    LLTAC_GET_PARAM {
        override fun toString() = "GET_PARAM"
    },

    LLTAC_ALLOC {
        override fun toString() = "ALLOC"
    },

    LLTAC_PARAM {
        override fun toString() = "PARAM"
    },

    LLTAC_ASSIGN {
        override fun toString() = "ASSIGN"
    },

    LLTAC_ADD {
        override fun toString() = "ADD"
    },
    LLTAC_SUB {
        override fun toString() = "SUB"
    },
    LLTAC_MUL {
        override fun toString() = "MUL"
    },
    LLTAC_DIV {
        override fun toString() = "DIV"
    },
    LLTAC_MOD {
        override fun toString() = "MOD"
    },
    LLTAC_EQ {
        override fun toString() = "EQ"
    },
    LLTAC_NEQ {
        override fun toString() = "NEQ"
    },
    LLTAC_LT {
        override fun toString() = "LT"
    },
    LLTAC_GT {
        override fun toString() = "GT"
    },
    LLTAC_LE {
        override fun toString() = "LE"
    },
    LLTAC_GE {
        override fun toString() = "GE"
    },
    LLTAC_AND {
        override fun toString() = "AND"
    },
    LLTAC_OR {
        override fun toString() = "OR"
    },
    LLTAC_CALL {
        override fun toString() = "CALL"
    },
    LLTAC_RET {
        override fun toString() = "RET"
    },
    LLTAC_NOT {
        override fun toString() = "NOT"
    },

    LLTAC_NEG {
        override fun toString() = "NEG"
    },

    LLTAC_JMP_IF_NOT {
        override fun toString() = "JMP_IF_NOT"
    },

    LLTAC_JMP {
        override fun toString() = "JMP"
    },

    LLTAC_LDX {
        override fun toString() = "LOAD"
    },

    LLTAC_STX {
        override fun toString() = "STORE"
    },

}