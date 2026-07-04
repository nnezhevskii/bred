package org.nnezh.bred.context

import org.nnezh.bred.ast.FunctionDeclAstNode
import org.nnezh.bred.common.TypeSign

public sealed interface FunctionMeta {
    val name: String
    val isTemplate: Boolean
}

data class BuiltInFunctionMeta(
    override val name: String,
    override val isTemplate: Boolean
): FunctionMeta

data class DeclaredFunctionMeta(
    override val name: String,
    override val isTemplate: Boolean,
    val astNode: FunctionDeclAstNode
): FunctionMeta

data class ConstructorMeta(val arguments: List<TypeSign>)
