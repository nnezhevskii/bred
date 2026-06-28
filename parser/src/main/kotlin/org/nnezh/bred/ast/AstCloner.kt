package org.nnezh.bred.ast

import org.nnezh.bred.context.ProgramGlobalContext
import org.nnezh.lexer.Position
import org.nnezh.lexer.Token

fun <T : ASTNode> cloneSubtree(node: T): T = AstCloner.cloneSubtree(node)

fun <T : ASTNode> T.deepClone(): T = AstCloner.cloneSubtree(this)

fun <T : ASTNode> cloneSubtreeReplacingType(
    node: T,
    template: String,
    type: String,
    context: ProgramGlobalContext,
): T = AstCloner.cloneSubtreeReplacingType(node, template, type, context)

fun <T : ASTNode> T.deepCloneReplacingType(
    template: String,
    type: String,
    context: ProgramGlobalContext,
): T = AstCloner.cloneSubtreeReplacingType(this, template, type, context)

object AstCloner {
    @Suppress("UNCHECKED_CAST")
    fun <T : ASTNode> cloneSubtree(node: T): T = cloneNode(node, null) as T

    @Suppress("UNCHECKED_CAST")
    fun <T : ASTNode> cloneSubtreeReplacingType(
        node: T,
        template: String,
        type: String,
        context: ProgramGlobalContext,
    ): T = cloneNode(node, TypeReplacement(template, type, context)) as T

    private data class TypeReplacement(
        val template: String,
        val type: String,
        val context: ProgramGlobalContext,
    )

    private fun cloneNode(node: ASTNode, replacement: TypeReplacement?): ASTNode =
        when (node) {
            is ProgramRoot -> ProgramRoot(
                functions = node.functions.map { cloneFunction(it, replacement) }.toMutableList(),
                globalVariables = node.globalVariables.map { cloneGlobalVariable(it, replacement) },
                types = node.types.map { cloneTypeDeclaration(it) },
                typeClasses = node.typeClasses.map { cloneTypeClass(it, replacement) },
                instances = node.instances.map { cloneInstance(it, replacement) },
            )
            is DeclareGlobalVariableASTNode -> cloneGlobalVariable(node, replacement)
            is DeclareTypeASTNode -> cloneTypeDeclaration(node)
            is FunctionDeclAstNode -> cloneFunction(node, replacement)
            is BlockAstNode -> cloneBlock(node, replacement)
            is StatementAstNode -> cloneStatement(node, replacement)
            is TypeClassDeclAstNode -> cloneTypeClass(node, replacement)
            is TypeClassMethodDeclAstNode -> cloneTypeClassMethod(node, replacement)
            is InstanceDeclAstNode -> cloneInstance(node, replacement)
            is ExpressionASTNode -> cloneExpression(node, replacement)
            EmptyNode -> EmptyNode
        }

    private fun cloneGlobalVariable(
        node: DeclareGlobalVariableASTNode,
        replacement: TypeReplacement?,
    ): DeclareGlobalVariableASTNode =
        DeclareGlobalVariableASTNode(
            name = node.name,
            type = node.type?.let { cloneTypeSign(it, replacement) },
            expression = node.expression?.let { cloneExpression(it, replacement) },
            isMutable = node.isMutable,
            size = node.size,
        )

    private fun cloneTypeDeclaration(node: DeclareTypeASTNode): DeclareTypeASTNode =
        DeclareTypeASTNode(node.name)

    private fun cloneFunction(
        node: FunctionDeclAstNode,
        replacement: TypeReplacement?,
    ): FunctionDeclAstNode =
        FunctionDeclAstNode(
            name = node.name,
            genericParams = cloneGenericParams(node.genericParams, replacement),
            arguments = node.arguments.map { cloneFunctionArgument(it, replacement) },
            result = cloneTypeSign(node.result, replacement),
            body = cloneBlock(node.body, replacement),
        )

    private fun cloneBlock(
        node: BlockAstNode,
        replacement: TypeReplacement?,
    ): BlockAstNode =
        BlockAstNode(node.statements.map { cloneStatement(it, replacement) })

    private fun cloneStatement(
        node: StatementAstNode,
        replacement: TypeReplacement?,
    ): StatementAstNode =
        when (node) {
            is ScalarVariableInitializationASTNode -> ScalarVariableInitializationASTNode(
                name = node.name,
                type = cloneTypeSign(node.type, replacement),
                expression = cloneExpression(node.expression, replacement),
                isMutable = node.isMutable,
            )
            is ArrayDeclarationASTNode -> ArrayDeclarationASTNode(
                name = node.name,
                type = cloneTypeSign(node.type, replacement),
                size = node.size,
                expression = node.expression?.let { cloneExpression(it, replacement) },
                isMutable = node.isMutable,
            )
            is AssignmentStatementAstNode -> AssignmentStatementAstNode(
                lValue = cloneExpression(node.lValue, replacement),
                rValue = cloneExpression(node.rValue, replacement),
            )
            is CallFunctionStatementAstNode -> CallFunctionStatementAstNode(
                expression = cloneExpression(node.expression, replacement),
            )
            is ReturnFunctionStatementAstNode -> ReturnFunctionStatementAstNode(
                expression = node.expression?.let { cloneExpression(it, replacement) },
                explicit = node.explicit,
            )
            is IfStatementAstNode -> IfStatementAstNode(
                condition = cloneExpression(node.condition, replacement),
                thenBlock = cloneBlock(node.thenBlock, replacement),
                elseBlock = node.elseBlock?.let { cloneBlock(it, replacement) },
            )
            is WhileStatementAstNode -> WhileStatementAstNode(
                condition = cloneExpression(node.condition, replacement),
                bodyBlock = cloneBlock(node.bodyBlock, replacement),
            )
            is ForStatementAstNode -> ForStatementAstNode(
                desugaredContent = cloneBlock(node.desugaredContent, replacement),
            )
        }

    private fun cloneExpression(
        node: ExpressionASTNode,
        replacement: TypeReplacement?,
    ): ExpressionASTNode =
        when (node) {
            is IntLiteralExpressionASTNode -> IntLiteralExpressionASTNode(node.value)
            is DoubleLiteralExpressionASTNode -> DoubleLiteralExpressionASTNode(node.value)
            is BooleanLiteralExpressionASTNode -> BooleanLiteralExpressionASTNode(node.value)
            is StringLiteralExpressionASTNode -> StringLiteralExpressionASTNode(node.value)
            is ArrayInitializationExpressionASTNode -> ArrayInitializationExpressionASTNode(
                args = node.args.map { cloneExpression(it, replacement) },
            )
            is VariableExpressionASTNode -> VariableExpressionASTNode(cloneToken(node.token))
            is ArrayElementAccessASTNode -> ArrayElementAccessASTNode(
                name = node.name,
                index = cloneExpression(node.index, replacement),
            )
            is FunctionCallExpressionASTNode -> FunctionCallExpressionASTNode(
                name = node.name,
                arguments = node.arguments.map { cloneExpression(it, replacement) },
            )
            is BinaryExpressionASTNode -> BinaryExpressionASTNode(
                left = cloneExpression(node.left, replacement),
                operator = cloneLocatedBinaryOperator(node.operator),
                right = cloneExpression(node.right, replacement),
            )
            is UnaryExpressionASTNode -> UnaryExpressionASTNode(
                operator = cloneLocatedUnaryOperator(node.operator),
                operand = cloneExpression(node.operand, replacement),
            )
        }

    private fun cloneTypeClass(
        node: TypeClassDeclAstNode,
        replacement: TypeReplacement?,
    ): TypeClassDeclAstNode =
        TypeClassDeclAstNode(
            name = node.name,
            genericParam = cloneGenericParam(node.genericParam),
            methods = node.methods.map { cloneTypeClassMethod(it, replacement) },
        )

    private fun cloneTypeClassMethod(
        node: TypeClassMethodDeclAstNode,
        replacement: TypeReplacement?,
    ): TypeClassMethodDeclAstNode =
        TypeClassMethodDeclAstNode(
            name = node.name,
            arguments = node.arguments.map { cloneFunctionArgument(it, replacement) },
            result = cloneTypeSign(node.result, replacement),
        )

    private fun cloneInstance(
        node: InstanceDeclAstNode,
        replacement: TypeReplacement?,
    ): InstanceDeclAstNode =
        InstanceDeclAstNode(
            typeClassName = node.typeClassName,
            type = cloneTypeSign(node.type, replacement),
            methods = node.methods.map { cloneFunction(it, replacement) },
        )

    fun cloneTypeSign(node: TypeSign): TypeSign = cloneTypeSign(node, null)

    fun cloneTypeSignReplacingType(
        node: TypeSign,
        template: String,
        type: String,
        context: ProgramGlobalContext,
    ): TypeSign = cloneTypeSign(node, TypeReplacement(template, type, context))

    private fun cloneTypeSign(node: TypeSign, replacement: TypeReplacement?): TypeSign =
        TypeSign(
            name = if (node.name == replacement?.template) replacement.type else node.name,
            args = node.args.map { cloneTypeSign(it, replacement) },
        )

    private fun cloneGenericParams(
        nodes: List<GenericParam>,
        replacement: TypeReplacement?,
    ): List<GenericParam> =
        nodes
            .filterNot { it.name == replacement?.template }
            .map { cloneGenericParam(it) }

    private fun cloneGenericParam(node: GenericParam): GenericParam =
        GenericParam(
            name = node.name,
            constraints = node.constraints.toList(),
        )

    private fun cloneFunctionArgument(
        node: FunctionArgument,
        replacement: TypeReplacement?,
    ): FunctionArgument =
        FunctionArgument(
            name = node.name,
            type = cloneTypeSign(node.type, replacement),
            isArray = node.isArray,
        )

    private fun cloneLocatedBinaryOperator(node: LocatedBinaryOperator): LocatedBinaryOperator =
        LocatedBinaryOperator(node.kind, clonePosition(node.position))

    private fun cloneLocatedUnaryOperator(node: LocatedUnaryOperator): LocatedUnaryOperator =
        LocatedUnaryOperator(node.kind, clonePosition(node.position))

    private fun cloneToken(token: Token): Token =
        when (token) {
            is Token.Keyword.Fun -> Token.Keyword.Fun(clonePosition(token.position))
            is Token.Keyword.Val -> Token.Keyword.Val(clonePosition(token.position))
            is Token.Keyword.Var -> Token.Keyword.Var(clonePosition(token.position))
            is Token.Keyword.If -> Token.Keyword.If(clonePosition(token.position))
            is Token.Keyword.Else -> Token.Keyword.Else(clonePosition(token.position))
            is Token.Keyword.Return -> Token.Keyword.Return(clonePosition(token.position))
            is Token.Keyword.While -> Token.Keyword.While(clonePosition(token.position))
            is Token.Keyword.For -> Token.Keyword.For(clonePosition(token.position))
            is Token.Keyword.To -> Token.Keyword.To(clonePosition(token.position))
            is Token.Keyword.Mut -> Token.Keyword.Mut(clonePosition(token.position))
            is Token.Keyword.In -> Token.Keyword.In(clonePosition(token.position))
            is Token.Keyword.True -> Token.Keyword.True(clonePosition(token.position))
            is Token.Keyword.False -> Token.Keyword.False(clonePosition(token.position))
            is Token.Keyword.Typeclass -> Token.Keyword.Typeclass(clonePosition(token.position))
            is Token.Keyword.Instance -> Token.Keyword.Instance(clonePosition(token.position))
            is Token.Identifier -> Token.Identifier(token.lexeme, clonePosition(token.position))
            is Token.Literal.IntLiteral -> Token.Literal.IntLiteral(token.value, token.lexeme, clonePosition(token.position))
            is Token.Literal.DoubleLiteral -> Token.Literal.DoubleLiteral(token.value, token.lexeme, clonePosition(token.position))
            is Token.Literal.StringLiteral -> Token.Literal.StringLiteral(token.value, token.lexeme, clonePosition(token.position))
            is Token.Operator.Plus -> Token.Operator.Plus(clonePosition(token.position))
            is Token.Operator.Minus -> Token.Operator.Minus(clonePosition(token.position))
            is Token.Operator.Star -> Token.Operator.Star(clonePosition(token.position))
            is Token.Operator.Slash -> Token.Operator.Slash(clonePosition(token.position))
            is Token.Operator.Percent -> Token.Operator.Percent(clonePosition(token.position))
            is Token.Operator.Assign -> Token.Operator.Assign(clonePosition(token.position))
            is Token.Operator.Eq -> Token.Operator.Eq(clonePosition(token.position))
            is Token.Operator.Neq -> Token.Operator.Neq(clonePosition(token.position))
            is Token.Operator.Lt -> Token.Operator.Lt(clonePosition(token.position))
            is Token.Operator.Gt -> Token.Operator.Gt(clonePosition(token.position))
            is Token.Operator.Le -> Token.Operator.Le(clonePosition(token.position))
            is Token.Operator.Ge -> Token.Operator.Ge(clonePosition(token.position))
            is Token.Operator.And -> Token.Operator.And(clonePosition(token.position))
            is Token.Operator.Or -> Token.Operator.Or(clonePosition(token.position))
            is Token.Operator.Not -> Token.Operator.Not(clonePosition(token.position))
            is Token.Punctuation.LParen -> Token.Punctuation.LParen(clonePosition(token.position))
            is Token.Punctuation.RParen -> Token.Punctuation.RParen(clonePosition(token.position))
            is Token.Punctuation.LBrace -> Token.Punctuation.LBrace(clonePosition(token.position))
            is Token.Punctuation.RBrace -> Token.Punctuation.RBrace(clonePosition(token.position))
            is Token.Punctuation.LBracket -> Token.Punctuation.LBracket(clonePosition(token.position))
            is Token.Punctuation.RBracket -> Token.Punctuation.RBracket(clonePosition(token.position))
            is Token.Punctuation.Comma -> Token.Punctuation.Comma(clonePosition(token.position))
            is Token.Punctuation.Colon -> Token.Punctuation.Colon(clonePosition(token.position))
            is Token.Punctuation.Dot -> Token.Punctuation.Dot(clonePosition(token.position))
            is Token.Eof -> Token.Eof(clonePosition(token.position))
        }

    private fun clonePosition(position: Position): Position =
        Position(position.line, position.column)
}
