package org.nnezh.bred.ast

import org.nnezh.bred.ast.parsers.AssignParser
import org.nnezh.bred.ast.parsers.BlockParser
import org.nnezh.bred.ast.parsers.CallStatementParser
import org.nnezh.bred.ast.parsers.ForParser
import org.nnezh.bred.ast.parsers.FunctionParser
import org.nnezh.bred.ast.parsers.IfParser
import org.nnezh.bred.ast.parsers.InstanceParser
import org.nnezh.bred.ast.parsers.ProgramParser
import org.nnezh.bred.ast.parsers.ReturnValueParser
import org.nnezh.bred.ast.parsers.StatementParser
import org.nnezh.bred.ast.parsers.TypeClassParser
import org.nnezh.bred.ast.parsers.TypeSignParser
import org.nnezh.bred.ast.parsers.VariableInitializationParser
import org.nnezh.bred.ast.parsers.WhileParser

class ParserFactory(
    private val expressionParser: Parser<ExpressionASTNode> = AbstractSyntaxTreeExpressionParser(),
    private val typeSignParser: TypeSignParser = TypeSignParser(),
) {
    private val variableInitializationParser: Parser<DeclareVariableASTNode> by lazy {
        VariableInitializationParser(expressionParser, typeSignParser)
    }
    private val assignParser: Parser<AssignmentStatementAstNode> by lazy { AssignParser(expressionParser) }
    private val callParser: Parser<CallFunctionStatementAstNode> by lazy { CallStatementParser(expressionParser) }
    private val ifParser: Parser<IfStatementAstNode> by lazy { IfParser(expressionParser, lazy { blockParser }) }
    private val whileParser: Parser<WhileStatementAstNode> by lazy { WhileParser(expressionParser, lazy { blockParser }) }
    private val forParser: Parser<ForStatementAstNode> by lazy { ForParser(expressionParser, lazy { blockParser }) }
    private val returnParser: Parser<ReturnFunctionStatementAstNode> by lazy { ReturnValueParser(expressionParser) }
    private val statementParser: Parser<StatementAstNode> by lazy {
        StatementParser(
            ifParser = ifParser,
            whileParser = whileParser,
            forParser = forParser,
            variableInitializationParser = variableInitializationParser,
            assignParser = assignParser,
            callParser = callParser,
            returnParser = returnParser,
        )
    }
    private val blockParser: Parser<BlockAstNode> by lazy { BlockParser(lazy { statementParser }) }
    private val functionParser: Parser<FunctionDeclAstNode> by lazy { FunctionParser(lazy { blockParser }, typeSignParser) }
    private val typeClassParser by lazy { TypeClassParser(typeSignParser) }
    private val instanceParser by lazy { InstanceParser(lazy { blockParser }, typeSignParser) }
    private val programParser: Parser<ProgramRoot> by lazy {
        ProgramParser(functionParser, variableInitializationParser, typeClassParser, instanceParser)
    }

    fun programParser(): Parser<ProgramRoot> = programParser
}
