package org.nnezh.org.nnezh.semantic.generic

import org.nnezh.ast.DeclareFunctionASTNode
import org.nnezh.org.nnezh.base.Type

class FunctionSignature(
    val name: String,
    val args: List<Type>,
    val returnType: Type
) {
    companion object {
        fun build(node: DeclareFunctionASTNode): FunctionSignature {
            val name: String = node.name
            val args: List<Type> = node.args.arguments.map { node -> node.type }
            val returnType = node.resultType

            return FunctionSignature(name, args, returnType)
        }
    }
}