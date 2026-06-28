package org.nnezh.org.nnezh.semantic

import org.nnezh.bred.ast.ProgramASTNode
import org.nnezh.org.nnezh.semantic.analyzers.ASTNodeTypeTable
import org.nnezh.org.nnezh.semantic.analyzers.FunctionRegistry
import org.nnezh.org.nnezh.semantic.analyzers.FunctionSubAnalyzer
import org.nnezh.org.nnezh.semantic.analyzers.SemanticControlFlowAnalyzer
import org.nnezh.org.nnezh.semantic.analyzers.TypeChecker
import org.nnezh.org.nnezh.semantic.analyzers.TypeValidator
import org.nnezh.org.nnezh.semantic.analyzers.VariableScopeSubAnalyzer
import org.nnezh.org.nnezh.semantic.generic.SemanticError

class SemanticAnalyzer {
    lateinit var typeTable: ASTNodeTypeTable
    lateinit var functionRegistry: FunctionRegistry

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
        functionRegistry = functionSubAnalyzer.registry
        val typeChecker = TypeChecker(
            functionRegistry = functionSubAnalyzer.registry,
            typeValidator = TypeValidator()
        )
        typeTable = typeChecker.typeScope.typeTable
        res.addAll(typeChecker.analyzeProgramASTNode(program))
        if (res.any { it.isCriticalError }) {
            return res
        }
        val semanticControlFlowAnalyzer = SemanticControlFlowAnalyzer(functionSubAnalyzer.registry)
        res.addAll(semanticControlFlowAnalyzer.analyzeProgramASTNode(program))

        return res
    }
}