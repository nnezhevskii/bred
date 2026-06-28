package org.nnezh.bred.ast

import org.nnezh.lexer.Position
import org.nnezh.lexer.Token

enum class BinaryOperator(val lexeme: String) {
    Plus("+"),
    Minus("-"),
    Star("*"),
    Slash("/"),
    Percent("%"),
    Eq("=="),
    Neq("!="),
    Lt("<"),
    Gt(">"),
    Le("<="),
    Ge(">="),
    And("&&"),
    Or("||"),
}

data class LocatedBinaryOperator(
    val kind: BinaryOperator,
    val position: Position,
) {
    val lexeme: String get() = kind.lexeme
}

fun Token.Operator.toLocatedBinaryOperator(): LocatedBinaryOperator = when (this) {
    is Token.Operator.Plus -> LocatedBinaryOperator(BinaryOperator.Plus, position)
    is Token.Operator.Minus -> LocatedBinaryOperator(BinaryOperator.Minus, position)
    is Token.Operator.Star -> LocatedBinaryOperator(BinaryOperator.Star, position)
    is Token.Operator.Slash -> LocatedBinaryOperator(BinaryOperator.Slash, position)
    is Token.Operator.Percent -> LocatedBinaryOperator(BinaryOperator.Percent, position)
    is Token.Operator.Eq -> LocatedBinaryOperator(BinaryOperator.Eq, position)
    is Token.Operator.Neq -> LocatedBinaryOperator(BinaryOperator.Neq, position)
    is Token.Operator.Lt -> LocatedBinaryOperator(BinaryOperator.Lt, position)
    is Token.Operator.Gt -> LocatedBinaryOperator(BinaryOperator.Gt, position)
    is Token.Operator.Le -> LocatedBinaryOperator(BinaryOperator.Le, position)
    is Token.Operator.Ge -> LocatedBinaryOperator(BinaryOperator.Ge, position)
    is Token.Operator.And -> LocatedBinaryOperator(BinaryOperator.And, position)
    is Token.Operator.Or -> LocatedBinaryOperator(BinaryOperator.Or, position)
    is Token.Operator.Assign,
    is Token.Operator.Not -> error("not a binary operator: $this")
}

fun LocatedBinaryOperator.toToken(): Token.Operator = when (kind) {
    BinaryOperator.Plus -> Token.Operator.Plus(position)
    BinaryOperator.Minus -> Token.Operator.Minus(position)
    BinaryOperator.Star -> Token.Operator.Star(position)
    BinaryOperator.Slash -> Token.Operator.Slash(position)
    BinaryOperator.Percent -> Token.Operator.Percent(position)
    BinaryOperator.Eq -> Token.Operator.Eq(position)
    BinaryOperator.Neq -> Token.Operator.Neq(position)
    BinaryOperator.Lt -> Token.Operator.Lt(position)
    BinaryOperator.Gt -> Token.Operator.Gt(position)
    BinaryOperator.Le -> Token.Operator.Le(position)
    BinaryOperator.Ge -> Token.Operator.Ge(position)
    BinaryOperator.And -> Token.Operator.And(position)
    BinaryOperator.Or -> Token.Operator.Or(position)
}
