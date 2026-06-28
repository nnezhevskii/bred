package org.nnezh.bred.ast

import java.io.PrintStream

class AstPrettyPrinter(
    private val indent: String = "  ",
) {
    fun print(root: ASTNode, out: PrintStream = System.out) {
        out.println(render(root))
    }

    fun render(root: ASTNode): String {
        val writer = Writer(indent)
        writer.node(root, 0)
        return writer.toString()
    }

    private class Writer(
        private val indent: String,
    ) {
        private val lines = mutableListOf<String>()

        fun node(node: ASTNode, level: Int) {
            when (node) {
                is ProgramRoot -> program(node, level)
                is DeclareGlobalVariableASTNode -> globalVariable(node, level)
                is DeclareTypeASTNode -> line(level, "type ${node.name}")
                is FunctionDeclAstNode -> function(node, level)
                is TypeClassDeclAstNode -> typeClass(node, level)
                is TypeClassMethodDeclAstNode -> line(level, methodSignature(node))
                is InstanceDeclAstNode -> instance(node, level)
                is BlockAstNode -> block(node, level)
                is StatementAstNode -> statement(node, level)
                is ExpressionASTNode -> expression(node, level)
                EmptyNode -> line(level, "<empty>")
            }
        }

        private fun program(node: ProgramRoot, level: Int) {
            line(level, "ProgramRoot")
            section(level + 1, "types", node.types)
            section(level + 1, "typeclasses", node.typeClasses)
            section(level + 1, "instances", node.instances)
            section(level + 1, "globals", node.globalVariables)
            section(level + 1, "functions", node.functions)
        }

        private fun globalVariable(node: DeclareGlobalVariableASTNode, level: Int) {
            val kind = if (node.isMutable) "var" else "val"
            val type = node.type?.let { ": ${type(it)}" }.orEmpty()
            val size = node.size?.let { "[$it]" }.orEmpty()
            line(level, "$kind ${node.name}$type$size")
            node.expression?.let {
                line(level + 1, "initializer:")
                expression(it, level + 2)
            }
        }

        private fun function(node: FunctionDeclAstNode, level: Int) {
            line(level, functionSignature(node))
            if (node.genericParams.isNotEmpty()) {
                line(level + 1, "generic params:")
                node.genericParams.forEach { line(level + 2, genericParam(it)) }
            }
            section(level + 1, "arguments", node.arguments) { arg, argLevel ->
                line(argLevel, argument(arg))
            }
            line(level + 1, "body:")
            block(node.body, level + 2)
        }

        private fun typeClass(node: TypeClassDeclAstNode, level: Int) {
            line(level, "typeclass ${node.name}<${genericParam(node.genericParam)}>")
            section(level + 1, "methods", node.methods)
        }

        private fun instance(node: InstanceDeclAstNode, level: Int) {
            line(level, "instance ${node.typeClassName}<${type(node.type)}>")
            section(level + 1, "methods", node.methods)
        }

        private fun block(node: BlockAstNode, level: Int) {
            if (node.statements.isEmpty()) {
                line(level, "block <empty>")
                return
            }
            line(level, "block")
            node.statements.forEach { statement(it, level + 1) }
        }

        private fun statement(node: StatementAstNode, level: Int) {
            when (node) {
                is ScalarVariableInitializationASTNode -> {
                    val kind = if (node.isMutable) "var" else "val"
                    line(level, "$kind ${node.name}: ${type(node.type)}")
                    line(level + 1, "initializer:")
                    expression(node.expression, level + 2)
                }
                is ArrayDeclarationASTNode -> {
                    val kind = if (node.isMutable) "var" else "val"
                    line(level, "$kind ${node.name}: ${type(node.type)}[${node.size}]")
                    node.expression?.let {
                        line(level + 1, "initializer:")
                        expression(it, level + 2)
                    }
                }
                is AssignmentStatementAstNode -> {
                    line(level, "assign")
                    line(level + 1, "left:")
                    expression(node.lValue, level + 2)
                    line(level + 1, "right:")
                    expression(node.rValue, level + 2)
                }
                is CallFunctionStatementAstNode -> {
                    line(level, "call statement")
                    expression(node.expression, level + 1)
                }
                is ReturnFunctionStatementAstNode -> {
                    val marker = if (node.explicit) "return" else "return <synthetic>"
                    line(level, marker)
                    node.expression?.let { expression(it, level + 1) } ?: line(level + 1, "Unit")
                }
                is IfStatementAstNode -> {
                    line(level, "if")
                    line(level + 1, "condition:")
                    expression(node.condition, level + 2)
                    line(level + 1, "then:")
                    block(node.thenBlock, level + 2)
                    node.elseBlock?.let {
                        line(level + 1, "else:")
                        block(it, level + 2)
                    }
                }
                is WhileStatementAstNode -> {
                    line(level, "while")
                    line(level + 1, "condition:")
                    expression(node.condition, level + 2)
                    line(level + 1, "body:")
                    block(node.bodyBlock, level + 2)
                }
                is ForStatementAstNode -> {
                    line(level, "for <desugared>")
                    block(node.desugaredContent, level + 1)
                }
            }
        }

        private fun expression(node: ExpressionASTNode, level: Int) {
            when (node) {
                is IntLiteralExpressionASTNode -> line(level, "int ${node.value}")
                is DoubleLiteralExpressionASTNode -> line(level, "double ${node.value}")
                is BooleanLiteralExpressionASTNode -> line(level, "boolean ${node.value}")
                is StringLiteralExpressionASTNode -> line(level, "string \"${escape(node.value)}\"")
                is VariableExpressionASTNode -> line(level, "variable ${node.token.lexeme}")
                is ArrayInitializationExpressionASTNode -> {
                    line(level, "array init")
                    node.args.forEachIndexed { index, arg ->
                        line(level + 1, "[$index]:")
                        expression(arg, level + 2)
                    }
                }
                is ArrayElementAccessASTNode -> {
                    line(level, "array access ${node.name}")
                    line(level + 1, "index:")
                    expression(node.index, level + 2)
                }
                is FunctionCallExpressionASTNode -> {
                    line(level, "call ${node.name}")
                    section(level + 1, "arguments", node.arguments) { arg, argLevel ->
                        expression(arg, argLevel)
                    }
                }
                is BinaryExpressionASTNode -> {
                    line(level, "binary ${node.operator.lexeme}")
                    line(level + 1, "left:")
                    expression(node.left, level + 2)
                    line(level + 1, "right:")
                    expression(node.right, level + 2)
                }
                is UnaryExpressionASTNode -> {
                    line(level, "unary ${node.operator.lexeme}")
                    expression(node.operand, level + 1)
                }
            }
        }

        private fun <T : ASTNode> section(level: Int, title: String, items: List<T>) {
            section(level, title, items) { item, itemLevel -> node(item, itemLevel) }
        }

        private fun <T> section(level: Int, title: String, items: List<T>, renderItem: (T, Int) -> Unit) {
            if (items.isEmpty()) {
                line(level, "$title: <empty>")
                return
            }
            line(level, "$title:")
            items.forEach { renderItem(it, level + 1) }
        }

        private fun functionSignature(node: FunctionDeclAstNode): String {
            val generics = node.genericParams.takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = "<", postfix = ">") { genericParam(it) }
                .orEmpty()
            val args = node.arguments.joinToString(", ") { argument(it) }
            return "fun ${node.name}$generics($args): ${type(node.result)}"
        }

        private fun methodSignature(node: TypeClassMethodDeclAstNode): String {
            val args = node.arguments.joinToString(", ") { argument(it) }
            return "fun ${node.name}($args): ${type(node.result)}"
        }

        private fun argument(node: FunctionArgument): String {
            val suffix = if (node.isArray) "[]" else ""
            return "${node.name}: ${type(node.type)}$suffix"
        }

        private fun genericParam(node: GenericParam): String =
            if (node.constraints.isEmpty()) {
                node.name
            } else {
                "${node.name}: ${node.constraints.joinToString(" + ")}"
            }

        private fun type(node: TypeSign): String {
            if (node.args.isEmpty()) {
                return node.name
            }
            return node.args.joinToString(prefix = "${node.name}<", postfix = ">") { type(it) }
        }

        private fun line(level: Int, text: String) {
            lines += indent.repeat(level) + text
        }

        private fun escape(value: String): String =
            value
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\"", "\\\"")

        override fun toString(): String = lines.joinToString(System.lineSeparator())
    }
}
