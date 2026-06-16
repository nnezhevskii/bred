/*
 * This file was generated with the assistance of AI (Cursor).
 */

package org.nnezh.lexer

import arrow.core.getOrElse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LexerTest {

    private fun tokenize(source: String): List<Token> =
        Lexer(source).tokenize().getOrElse { error("unexpected lexer error: $it") }

    /** Tokens without the trailing EOF, for convenient assertions. */
    private fun meaningfulTokens(source: String): List<Token> =
        tokenize(source).dropLast(1)

    // region Positive scenarios

    @Test
    fun `empty input produces only EOF`() {
        val tokens = tokenize("")
        assertEquals(1, tokens.size)
        assertInstanceOf(Token.Eof::class.java, tokens[0])
        assertEquals(Position(1, 1), tokens[0].position)
    }

    @Test
    fun `blank input produces only EOF`() {
        val tokens = tokenize("   \n\t  \n")
        assertEquals(1, tokens.size)
        assertInstanceOf(Token.Eof::class.java, tokens.single())
    }

    @Test
    fun `keywords are recognized as keyword tokens`() {
        val tokens = meaningfulTokens("fun val var if else return while true false")
        val expected = listOf(
            Token.Keyword.Fun::class.java,
            Token.Keyword.Val::class.java,
            Token.Keyword.Var::class.java,
            Token.Keyword.If::class.java,
            Token.Keyword.Else::class.java,
            Token.Keyword.Return::class.java,
            Token.Keyword.While::class.java,
            Token.Keyword.True::class.java,
            Token.Keyword.False::class.java,
        )
        assertEquals(expected.size, tokens.size)
        expected.forEachIndexed { i, type -> assertInstanceOf(type, tokens[i]) }
    }

    @Test
    fun `identifiers that look like keywords are still identifiers`() {
        val tokens = meaningfulTokens("function valuable iffy _private x1 readInt")
        assertTrue(tokens.all { it is Token.Identifier })
        assertEquals(
            listOf("function", "valuable", "iffy", "_private", "x1", "readInt"),
            tokens.map { it.lexeme },
        )
    }

    @Test
    fun `integer literals are parsed with their numeric value`() {
        val tokens = meaningfulTokens("0 7 42 1000000")
        val literals = tokens.map { assertInstanceOf(Token.Literal.IntLiteral::class.java, it) }
        assertEquals(listOf(0L, 7L, 42L, 1_000_000L), literals.map { it.value })
    }

    @Test
    fun `double literals are parsed with their numeric value`() {
        val tokens = meaningfulTokens("3.14 2.0 0.5")
        val literals = tokens.map { assertInstanceOf(Token.Literal.DoubleLiteral::class.java, it) }
        assertEquals(listOf(3.14, 2.0, 0.5), literals.map { it.value })
    }

    @Test
    fun `dot without trailing digit is an int followed by a dot`() {
        val tokens = meaningfulTokens("3.")
        assertEquals(2, tokens.size)
        val int = assertInstanceOf(Token.Literal.IntLiteral::class.java, tokens[0])
        assertEquals(3L, int.value)
        assertInstanceOf(Token.Punctuation.Dot::class.java, tokens[1])
    }

    @Test
    fun `int and double can be mixed in one expression`() {
        val tokens = meaningfulTokens("1 + 2.5")
        assertInstanceOf(Token.Literal.IntLiteral::class.java, tokens[0])
        assertInstanceOf(Token.Operator.Plus::class.java, tokens[1])
        val d = assertInstanceOf(Token.Literal.DoubleLiteral::class.java, tokens[2])
        assertEquals(2.5, d.value)
    }

    @Test
    fun `double glued to identifier is reported`() {
        val error = assertInstanceOf(LexerError.InvalidNumber::class.java, errorOf("3.14abc"))
        assertTrue(error.message.contains("Invalid number literal"))
    }

    @Test
    fun `string literal decodes escape sequences but keeps raw lexeme`() {
        val token = meaningfulTokens(""""line\n\t\"quote\"\\"""").single()
        val string = assertInstanceOf(Token.Literal.StringLiteral::class.java, token)
        assertEquals("line\n\t\"quote\"\\", string.value)
        assertEquals(""""line\n\t\"quote\"\\"""", string.lexeme)
    }

    @Test
    fun `empty string literal is valid`() {
        val token = meaningfulTokens("\"\"").single()
        val string = assertInstanceOf(Token.Literal.StringLiteral::class.java, token)
        assertEquals("", string.value)
    }

    @Test
    fun `single and double character operators are distinguished`() {
        val tokens = meaningfulTokens("+ - * / % = == != < > <= >= && || !")
        val expected = listOf(
            Token.Operator.Plus::class.java,
            Token.Operator.Minus::class.java,
            Token.Operator.Star::class.java,
            Token.Operator.Slash::class.java,
            Token.Operator.Percent::class.java,
            Token.Operator.Assign::class.java,
            Token.Operator.Eq::class.java,
            Token.Operator.Neq::class.java,
            Token.Operator.Lt::class.java,
            Token.Operator.Gt::class.java,
            Token.Operator.Le::class.java,
            Token.Operator.Ge::class.java,
            Token.Operator.And::class.java,
            Token.Operator.Or::class.java,
            Token.Operator.Not::class.java,
        )
        assertEquals(expected.size, tokens.size)
        expected.forEachIndexed { i, type -> assertInstanceOf(type, tokens[i]) }
    }

    @Test
    fun `punctuation is recognized`() {
        val tokens = meaningfulTokens("(){},:.")
        val expected = listOf(
            Token.Punctuation.LParen::class.java,
            Token.Punctuation.RParen::class.java,
            Token.Punctuation.LBrace::class.java,
            Token.Punctuation.RBrace::class.java,
            Token.Punctuation.Comma::class.java,
            Token.Punctuation.Colon::class.java,
            Token.Punctuation.Dot::class.java,
        )
        assertEquals(expected.size, tokens.size)
        expected.forEachIndexed { i, type -> assertInstanceOf(type, tokens[i]) }
    }

    @Test
    fun `semicolon is rejected`() {
        val error = assertInstanceOf(LexerError.UnexpectedCharacter::class.java, errorOf(";"))
        assertEquals(';', error.char)
        assertTrue(error.message.contains("Unexpected character ';'"))
    }

    @Test
    fun `semicolon between statements is rejected`() {
        val error = assertInstanceOf(
            LexerError.UnexpectedCharacter::class.java,
            errorOf("val a: Int = 1; val b: Int = 2"),
        )
        assertEquals(';', error.char)
        assertTrue(error.message.contains("Unexpected character ';'"))
    }

    @Test
    fun `line and block comments are skipped`() {
        val source = """
            // leading line comment
            val x /* inline */ = 1 // trailing
            /* multi
               line
               comment */
            val y = 2
        """.trimIndent()
        val tokens = meaningfulTokens(source)
        assertEquals(
            listOf("val", "x", "=", "1", "val", "y", "=", "2"),
            tokens.map { it.lexeme },
        )
    }

    @Test
    fun `positions track lines and columns`() {
        val tokens = meaningfulTokens("val x\n  = 1")
        assertEquals(Position(1, 1), tokens[0].position) // val
        assertEquals(Position(1, 5), tokens[1].position) // x
        assertEquals(Position(2, 3), tokens[2].position) // =
        assertEquals(Position(2, 5), tokens[3].position) // 1
    }

    @Test
    fun `full example program tokenizes without errors`() {
        val source = readSource("examples/max.bred").getOrElse { error("cannot read example: $it") }
        val tokens = tokenize(source)

        // First statement: fun max ( a : Int , ...
        assertInstanceOf(Token.Keyword.Fun::class.java, tokens[0])
        val name = assertInstanceOf(Token.Identifier::class.java, tokens[1])
        assertEquals("max", name.lexeme)
        assertInstanceOf(Token.Punctuation.LParen::class.java, tokens[2])

        // Sanity: ends with EOF and contains the expected string literal.
        assertInstanceOf(Token.Eof::class.java, tokens.last())
        val strings = tokens.filterIsInstance<Token.Literal.StringLiteral>().map { it.value }
        assertEquals(
            listOf("first value is bigger", "second value is bigger"),
            strings,
        )
    }

    // endregion

    // region Negative scenarios

    private fun errorOf(source: String): LexerError =
        Lexer(source).tokenize().leftOrNull() ?: error("expected a lexer error for: $source")

    @Test
    fun `unknown character is reported with position`() {
        val error = assertInstanceOf(LexerError.UnexpectedCharacter::class.java, errorOf("val x = @"))
        assertEquals(Position(1, 9), error.position)
        assertEquals('@', error.char)
    }

    @Test
    fun `unterminated string is reported`() {
        val error = assertInstanceOf(LexerError.UnterminatedString::class.java, errorOf("\"no closing quote"))
        assertEquals(Position(1, 1), error.position)
    }

    @Test
    fun `string broken by newline is reported`() {
        assertInstanceOf(LexerError.UnterminatedString::class.java, errorOf("\"line\nbreak\""))
    }

    @Test
    fun `unterminated block comment is reported with start position`() {
        val error = assertInstanceOf(
            LexerError.UnterminatedBlockComment::class.java,
            errorOf("val x = 1\n/* open"),
        )
        assertEquals(Position(2, 1), error.position)
    }

    @Test
    fun `unknown escape sequence is reported`() {
        val error = assertInstanceOf(LexerError.UnknownEscape::class.java, errorOf(""""bad \q escape""""))
        assertEquals('q', error.escape)
    }

    @Test
    fun `number glued to identifier is reported`() {
        val error = assertInstanceOf(LexerError.InvalidNumber::class.java, errorOf("12abc"))
        assertTrue(error.message.contains("Invalid number literal"))
    }

    @Test
    fun `lonely ampersand is reported`() {
        val error = assertInstanceOf(LexerError.UnexpectedCharacter::class.java, errorOf("a & b"))
        assertEquals('&', error.char)
        assertTrue(error.message.contains("&&"))
    }

    // endregion
}
