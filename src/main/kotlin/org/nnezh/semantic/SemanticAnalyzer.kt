package org.nnezh.org.nnezh.semantic

import com.sun.org.apache.xpath.internal.operations.Variable
import org.nnezh.ast.ProgramASTNode
import org.nnezh.org.nnezh.semantic.analyzers.FunctionSubAnalyzer
import org.nnezh.org.nnezh.semantic.analyzers.TypeChecker
import org.nnezh.org.nnezh.semantic.analyzers.TypeValidator
import org.nnezh.org.nnezh.semantic.analyzers.VariableScopeSubAnalyzer
import org.nnezh.org.nnezh.semantic.generic.SemanticError
import org.nnezh.org.nnezh.semantic.generic.SemanticSubAnalyzer

class SemanticAnalyzer {
//    private val pipeline: List<SemanticSubAnalyzer> = mutableListOf(
//        VariableScopeSubAnalyzer(),
//        FunctionSubAnalyzer()
//    )

    operator fun invoke(program: ProgramASTNode): List<SemanticError> {
        val res = VariableScopeSubAnalyzer().analyzeProgramASTNode(program).toMutableList()
        if (res.any { it.isCriticalError }) {
            return res
        }

        val functionSubAnalyzer = FunctionSubAnalyzer()
        res.addAll(functionSubAnalyzer.analyzeProgramASTNode(program))
        if (res.any { it.isCriticalError }) {
            return res
        }
        val typeChecker = TypeChecker(
            functionRegistry = functionSubAnalyzer.registry,
            typeValidator = TypeValidator()
        )
        res.addAll(typeChecker.analyzeProgramASTNode(program))

        return res
    }
}