package org.nnezh.org.nnezh.ast

import org.nnezh.ast.AssignmentStatementASTNode
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.CallFunctionStatementASTNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.ForStatementASTNode
import org.nnezh.ast.IfStatementASTNode
import org.nnezh.ast.ImmutableVariableInitializationASTNode
import org.nnezh.ast.ProgramASTNode
import org.nnezh.ast.ReturnFunctionStatementASTNode
import org.nnezh.ast.WhileStatementASTNode

class ParserFactory(
    private val expressionParser: Parser<ExpressionASTNode> = AbstractSyntaxTreeExpressionParser(),
    private val createIf: (Parser<ExpressionASTNode>, Lazy<Parser<BlockASTNode>>) -> Parser<IfStatementASTNode> = { e, b -> IfParser(e, b) },
    private val createWhile: (Parser<ExpressionASTNode>, Lazy<Parser<BlockASTNode>>) -> Parser<WhileStatementASTNode> = { e, b -> WhileParser(e, b) },
    private val createFor: (Parser<ExpressionASTNode>, Lazy<Parser<BlockASTNode>>) -> Parser<ForStatementASTNode> = { e, b -> ForParser(e, b) },
    private val createInit: (Parser<ExpressionASTNode>) -> Parser<ImmutableVariableInitializationASTNode> = { e -> ImmutableInitializationParser(e) },
    private val createAssign: (Parser<ExpressionASTNode>) -> Parser<AssignmentStatementASTNode> = { e -> AssignParser(e) },
    private val createCall: (Parser<ExpressionASTNode>) -> Parser<CallFunctionStatementASTNode> = { e -> CallStatementParser(e) },
    private val createReturn: (Parser<ExpressionASTNode>) -> Parser<ReturnFunctionStatementASTNode> = { e -> ReturnValueParser(e) },
) {
    private val initParser by lazy { createInit(expressionParser) }
    private val assignParser by lazy { createAssign(expressionParser) }
    private val callParser by lazy { createCall(expressionParser) }
    private val ifParser by lazy { createIf(expressionParser, lazy { blockParser }) }
    private val whileParser by lazy { createWhile(expressionParser, lazy { blockParser }) }
    private val forParser by lazy { createFor(expressionParser, lazy { blockParser }) }
    private val returnValueParser by lazy { createReturn(expressionParser) }
    private val statementParser by lazy {
        StatementParser(ifParser, whileParser, forParser, initParser, assignParser, callParser, returnValueParser)
    }
    private val blockParser: Parser<BlockASTNode> by lazy { BlockParser(lazy { statementParser }) }
    private val functionParser by lazy { FunctionParser(lazy { blockParser }) }
    private val programParser by lazy { ProgramParser(functionParser, initParser) }

    fun programParser(): Parser<ProgramASTNode> = programParser
}
