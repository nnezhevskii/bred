package org.nnezh.org.nnezh.ast

import org.nnezh.ast.AssignmentStatementASTNode
import org.nnezh.ast.BlockASTNode
import org.nnezh.ast.CallFunctionStatementASTNode
import org.nnezh.ast.ExpressionASTNode
import org.nnezh.ast.ForStatementASTNode
import org.nnezh.ast.IfStatementASTNode
import org.nnezh.ast.ImmutableVariableInitializationASTNode
import org.nnezh.ast.MutableVariableInitializationASTNode
import org.nnezh.ast.ProgramASTNode
import org.nnezh.ast.ReturnFunctionStatementASTNode
import org.nnezh.ast.VariableInitializationASTNode
import org.nnezh.ast.WhileStatementASTNode
import org.nnezh.org.nnezh.ast.parsers.AssignParser
import org.nnezh.org.nnezh.ast.parsers.BlockParser
import org.nnezh.org.nnezh.ast.parsers.CallStatementParser
import org.nnezh.org.nnezh.ast.parsers.ForParser
import org.nnezh.org.nnezh.ast.parsers.FunctionParser
import org.nnezh.org.nnezh.ast.parsers.IfParser
import org.nnezh.org.nnezh.ast.parsers.ImmutableInitializationParser
import org.nnezh.org.nnezh.ast.parsers.MutableInitializationParser
import org.nnezh.org.nnezh.ast.parsers.Parser
import org.nnezh.org.nnezh.ast.parsers.ProgramParser
import org.nnezh.org.nnezh.ast.parsers.ReturnValueParser
import org.nnezh.org.nnezh.ast.parsers.StatementParser
import org.nnezh.org.nnezh.ast.parsers.WhileParser

class ParserFactory(
    private val expressionParser: Parser<ExpressionASTNode> = AbstractSyntaxTreeExpressionParser(),
    private val createIf: (Parser<ExpressionASTNode>, Lazy<Parser<BlockASTNode>>) -> Parser<IfStatementASTNode> = { e, b ->
        IfParser(
            e,
            b
        )
    },
    private val createWhile: (Parser<ExpressionASTNode>, Lazy<Parser<BlockASTNode>>) -> Parser<WhileStatementASTNode> = { e, b ->
        WhileParser(
            e,
            b
        )
    },
    private val createFor: (Parser<ExpressionASTNode>, Lazy<Parser<BlockASTNode>>) -> Parser<ForStatementASTNode> = { e, b ->
        ForParser(
            e,
            b
        )
    },
    private val createInitImmutableParser: (Parser<ExpressionASTNode>) -> Parser<VariableInitializationASTNode> = { e ->
        ImmutableInitializationParser(
            e
        )
    },
    private val createInitMutableParser: (Parser<ExpressionASTNode>) -> Parser<MutableVariableInitializationASTNode> = { e ->
        MutableInitializationParser(
            e
        )
    },
    private val createAssign: (Parser<ExpressionASTNode>) -> Parser<AssignmentStatementASTNode> = { e -> AssignParser(e) },
    private val createCall: (Parser<ExpressionASTNode>) -> Parser<CallFunctionStatementASTNode> = { e ->
        CallStatementParser(
            e
        )
    },
    private val createReturn: (Parser<ExpressionASTNode>) -> Parser<ReturnFunctionStatementASTNode> = { e ->
        ReturnValueParser(
            e
        )
    },
) {
    private val initImmutableParser by lazy { createInitImmutableParser(expressionParser) }
    private val initMutableParser by lazy { createInitMutableParser(expressionParser) }
    private val assignParser by lazy { createAssign(expressionParser) }
    private val callParser by lazy { createCall(expressionParser) }
    private val ifParser by lazy { createIf(expressionParser, lazy { blockParser }) }
    private val whileParser by lazy { createWhile(expressionParser, lazy { blockParser }) }
    private val forParser by lazy { createFor(expressionParser, lazy { blockParser }) }
    private val returnValueParser by lazy { createReturn(expressionParser) }
    private val statementParser by lazy {
        StatementParser(
            ifParser,
            whileParser,
            forParser,
            initImmutableParser,
            initMutableParser,
            assignParser,
            callParser,
            returnValueParser
        )
    }
    private val blockParser: Parser<BlockASTNode> by lazy { BlockParser(lazy { statementParser }) }
    private val functionParser by lazy { FunctionParser(lazy { blockParser }) }
    private val programParser by lazy { ProgramParser(functionParser, initImmutableParser) }

    fun programParser(): Parser<ProgramASTNode> = programParser
}
