package org.nnezh.org.nnezh.semantic

import com.sun.org.apache.xpath.internal.operations.Variable
import org.nnezh.ast.ProgramASTNode
import org.nnezh.org.nnezh.semantic.analyzers.VariableScopeSubAnalyzer
import org.nnezh.org.nnezh.semantic.generic.SemanticError
import org.nnezh.org.nnezh.semantic.generic.SemanticSubAnalyzer

class SemanticAnalyzer {
    private val pipeline: List<SemanticSubAnalyzer> = mutableListOf(
        VariableScopeSubAnalyzer()
    )

    operator fun invoke(program: ProgramASTNode): List<SemanticError> {
        return pipeline.flatMap { analyzer -> analyzer.analyzeProgramASTNode(program) }
    }
}