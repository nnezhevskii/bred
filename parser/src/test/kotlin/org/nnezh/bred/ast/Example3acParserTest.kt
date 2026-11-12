package org.nnezh.bred.ast

import arrow.core.getOrElse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nnezh.lexer.Lexer
import java.nio.file.Files
import java.nio.file.Path

//class Example3acParserTest {
//    @Test
//    fun `deployment health radar example keeps sum and product declarations parseable`() {
//        val root = parseExample()
//
//        val health = assertInstanceOf(DeclareSumTypeASTNode::class.java, root.types.single { it.name == "Health" })
//        val alert = assertInstanceOf(DeclareSumTypeASTNode::class.java, root.types.single { it.name == "Alert" })
//        val metrics = assertInstanceOf(DeclareProductTypeASTNode::class.java, root.types.single { it.name == "Metrics" })
//        val audit = assertInstanceOf(DeclareProductTypeASTNode::class.java, root.types.single { it.name == "FleetAudit" })
//
//        assertEquals(listOf("Healthy", "Degraded", "Critical", "Offline"), health.variants.map { it.name })
//        assertEquals(listOf("NoAlert", "Notify"), alert.variants.map { it.name })
//        assertEquals(listOf("latencyMs", "errors", "staleMinutes"), metrics.fields.keys.toList())
//        assertTrue(audit.isMutable)
//        assertEquals(7, audit.fields.size)
//    }
//
//    @Test
//    fun `deployment health radar example parses sum payload patterns in when branches`() {
//        val patterns = parseExample()
//            .functions
//            .flatMap { it.body.statements }
//            .filterIsInstance<WhenStatementAstNode>()
//            .flatMap { it.expression.branches }
//            .mapNotNull { it.condition as? FunctionCallExpressionASTNode }
//
//        assertTrue(patterns.any { it.name == "Degraded" && it.bindingNames() == listOf("score") })
//        assertTrue(patterns.any { it.name == "Critical" && it.bindingNames() == listOf("score") })
//        assertTrue(patterns.any { it.name == "Just" && it.bindingNames() == listOf("bestScore") })
//        assertTrue(patterns.any { it.name == "Notify" && it.bindingNames() == listOf("level", "message") })
//    }
//
//    private fun parseExample(): ProgramRoot {
//        val source = Files.readString(Path.of("examples/3ac.bred"))
//        val tokens = Lexer(source).tokenize().getOrElse { error("unexpected lexer error: $it") }
//        val ast = AbstractSyntaxTreeBuilder().build(tokens).getOrElse { error("unexpected parser error: $it") }
//        return assertInstanceOf(ProgramRoot::class.java, ast)
//    }
//
//    private fun FunctionCallExpressionASTNode.bindingNames(): List<String> =
//        arguments.map { argument ->
//            assertInstanceOf(VariableExpressionASTNode::class.java, argument).token.lexeme
//        }
//}
