package org.nnezh.ast

import org.nnezh.lexer.Position
import org.nnezh.lexer.Token

enum class UnaryOperator(val lexeme: String) {
    Minus("-"),
    Not("!"),
}

data class LocatedUnaryOperator(
    val kind: UnaryOperator,
    val position: Position,
) {
    val lexeme: String get() = kind.lexeme
}

fun Token.Operator.toLocatedUnaryOperator(): LocatedUnaryOperator = when (this) {
    is Token.Operator.Minus -> LocatedUnaryOperator(UnaryOperator.Minus, position)
    is Token.Operator.Not -> LocatedUnaryOperator(UnaryOperator.Not, position)
    else -> error("not a unary operator: $this")
}

fun LocatedUnaryOperator.toToken(): Token.Operator = when (kind) {
    UnaryOperator.Minus -> Token.Operator.Minus(position)
    UnaryOperator.Not -> Token.Operator.Not(position)
}
