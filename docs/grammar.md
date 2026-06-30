# bred - Grammar Reference

This document describes the syntax accepted by the current `lexer` and `parser`
modules. The `main` module is not used as a source for this document.

Source of truth:

- `lexer/src/main/kotlin/org/nnezh/lexer`
- `lexer/src/test/kotlin/org/nnezh/lexer`
- `parser/src/main/kotlin/org/nnezh/bred/ast`
- `parser/src/test/kotlin/org/nnezh/bred/ast/ParserTest.kt`

Semantic checks are documented in `docs/semantic_analysis_draft.md`.

## 1. Lexical Structure

The lexer is hand-written and returns either the first `LexerError` or a token
list ending with one synthetic `EOF` token.

```ebnf
letter      ::= Char.isLetter ;
digit       ::= Char.isDigit ;
identifier  ::= ( letter | "_" ) { letter | digit | "_" } ;
```

Identifiers are Unicode-aware through Kotlin character predicates. After an
identifier is scanned, exact text is matched against the keyword table.

### 1.1 Keywords

```text
fun val var if else return while for in to true false mut typeclass instance
```

Keywords are case-sensitive. Prefixes and longer words are identifiers:
`function`, `valuable`, `typeclasses`, `instanceOf`.

`mut` is lexed as a keyword but is not consumed by the current parser.

### 1.2 Literals

```ebnf
intLiteral     ::= digit { digit } ;
doubleLiteral  ::= digit { digit } "." digit { digit } ;
stringLiteral  ::= '"' { stringChar | escapeSequence } '"' ;
escapeSequence ::= "\" ( "n" | "t" | "r" | '"' | "\" | "0" ) ;
booleanLiteral ::= "true" | "false" ;
```

Integer literals are parsed as `Long` by the lexer and stored as `Int` in the
AST expression node. Integer overflow is a lexer error.

A dot starts a double literal only when followed by a digit. Therefore `3.`
lexes as `3` and `.`, `.5` lexes as `.` and `5`, and `1..2` lexes as
`1`, `.`, `.`, `2`.

Numbers glued to an identifier or underscore, for example `12abc` or `1_000`,
are lexer errors.

String literals decode `\n`, `\t`, `\r`, `\"`, `\\`, and `\0`. Raw newlines,
unknown escapes, EOF inside a string, and a trailing backslash produce lexer
errors.

### 1.3 Operators

```text
+  -  *  /  %  =  ==  !=  <  >  <=  >=  &&  ||  !
```

The lexer recognizes multi-character operators greedily. A lone `&` or `|` is
a lexer error with a hint for `&&` or `||`.

### 1.4 Punctuation

```text
( ) { } [ ] , : .
```

`;` is rejected by the lexer. The dot token exists but is not part of the
current expression grammar.

### 1.5 Whitespace and Comments

Whitespace satisfying `Char.isWhitespace()` is skipped.

```ebnf
lineComment  ::= "//" { anyCharExceptNewline } ;
blockComment ::= "/*" { anyChar } "*/" ;
```

Comments do not produce tokens. Block comments are not nested.

## 2. Program

```ebnf
program      ::= { topLevelDecl } EOF ;
topLevelDecl ::= functionDecl
               | globalValDecl
               | typeclassDecl
               | instanceDecl ;
```

Top-level `var`, statements, assignments, and call statements are rejected.
Top-level declarations are stored in separate `ProgramRoot` lists; the AST does
not preserve one combined top-level declaration order.

## 3. Types

The parser stores types as `TypeSign(name, args)` and does not validate type
names syntactically.

```ebnf
typeSign ::= identifier [ "<" typeSign { "," typeSign } ">" ] ;
```

Examples:

```bred
Int
String
List<Int>
Pair<A, B>
Codec<List<Int>>
```

Empty type argument lists are invalid: `Box<>`. Trailing commas are invalid:
`Pair<A,>`.

Primitive names used by tests and built-ins are `Int`, `Double`, `String`,
`Boolean`, and `Unit`.

## 4. Functions

```ebnf
functionDecl  ::= "fun" identifier genericParams? functionParams returnType? block ;
genericParams ::= "<" genericParam { "," genericParam } ">" ;
genericParam  ::= identifier [ ":" identifier ] ;
functionParams ::= "(" [ functionParam { "," functionParam } ] ")" ;
functionParam ::= identifier ":" typeSign [ "[]" ] ;
returnType    ::= ":" typeSign ;
```

If `returnType` is omitted and the next token is `{`, the parser assigns
`Unit`.

Examples:

```bred
fun main(): Unit { }
fun main() { }
fun id(value: Int): Int { return value }
fun first(values: Int[]): Int { return values[0] }
fun prettyPrint<A: Printable>(a: A) { println(toPrettyPrinter(a)) }
fun pair<A: Printable, B>(a: A, b: B): Pair<A, B> { return makePair(a, b) }
```

Parameter arrays use `Type[]` and are represented as a normal `TypeSign` plus
`FunctionArgument.isArray = true`. Sized parameter arrays such as `Int[3]` are
not supported.

Trailing commas in generic parameter lists and function parameter lists are
invalid.

### 4.1 Synthetic Return

After parsing a function body, if the top-level block has no
`ReturnFunctionStatementAstNode`, the parser appends:

```text
ReturnFunctionStatementAstNode(expression = null, explicit = false)
```

This happens regardless of the declared return type. The semantic analyzer must
reject a non-`Unit` function whose only function-level return is synthetic.
Nested returns do not suppress the synthetic return.

## 5. Typeclasses and Instances

```ebnf
typeclassDecl ::= "typeclass" identifier "<" identifier ">"
                  "{" { typeclassMethod } "}" ;

typeclassMethod ::= "fun" identifier functionParams [ ":" typeSign ] ;

instanceDecl ::= "instance" typeSign "{" { functionDecl } "}" ;
```

Typeclass declarations require exactly one unconstrained generic parameter.
Typeclass methods are signatures only; method bodies are invalid. If a method
result type is omitted, it defaults to `Unit`.

An instance header must be a type sign with exactly one type argument. The
outer type name is the typeclass name and the single argument is the target
type.

Examples:

```bred
typeclass Printable<A> {
    fun toPrettyPrinter(a: A): String
}

instance Printable<Int> {
    fun toPrettyPrinter(a: Int): String {
        return intToString(a)
    }
}

instance Codec<List<Int>> {
    fun encode(values: List<Int>): String {
        return stringify(values)
    }
}
```

Invalid forms:

```bred
typeclass Printable { fun print(a: Int) }
typeclass Printable<A> { fun print(a: A) { println(a) } }
instance Printable { }
instance Printable<Int, String> { }
```

## 6. Variables and Arrays

```ebnf
globalValDecl ::= valDecl ;

valDecl        ::= scalarValDecl | staticArrayValDecl ;
varDecl        ::= scalarVarDecl ;

scalarValDecl  ::= "val" identifier ":" typeSign "=" expression ;
scalarVarDecl  ::= "var" identifier ":" typeSign "=" expression ;

staticArrayValDecl ::= "val" identifier ":" typeSign "[" intLiteral "]"
                       [ "=" arrayInitList ] ;

arrayInitList  ::= "[" [ expression { "," expression } ] "]" ;
```

Only `val` arrays are parsed by `VariableInitializationParser`. `var xs:
Int[3]` is not accepted by the array-declaration branch.

Examples:

```bred
val answer: Int = 42
val values: Int[3]
val values: Int[3] = [1, 2, 3]

fun main(): Unit {
    val n: Int = 1
    var total: Int = 0
}
```

Array sizes in declarations must be integer literals. `val xs: Int[n]` and
`val xs: Int[]` are invalid. Array initializers reject scalar right-hand sides,
trailing commas, and consecutive commas.

## 7. Blocks and Statements

```ebnf
block     ::= "{" { statement } "}" ;

statement ::= valDecl
            | varDecl
            | assignment
            | callStmt
            | returnStmt
            | ifStmt
            | whileStmt
            | forStmt ;
```

Statements have no semicolon separator. Newlines are not statement
terminators.

Statement dispatch is based on the first token:

| First token | Route |
|-------------|-------|
| `val`, `var` | variable declaration |
| `return` | return statement |
| `if` | if statement |
| `while` | while statement |
| `for` | for statement |
| `identifier` followed by `(` | call statement |
| `identifier` otherwise | assignment |

Bare expression statements, indirect call statements such as `(f)()`, and
literal-starting statements are rejected.

### 7.1 Assignment

```ebnf
assignment  ::= lvalue "=" expression ;
lvalue      ::= identifier | arrayAccess ;
arrayAccess ::= identifier "[" expression "]" ;
```

The assignment parser first parses the left side as an expression and then
requires it to be either `VariableExpressionASTNode` or
`ArrayElementAccessASTNode`.

### 7.2 Return

```ebnf
returnStmt ::= "return" [ "Unit" | expression ] ;
```

`return Unit` is represented as `expression = null, explicit = true`. A bare
`return` immediately before `}` is also represented as explicit Unit. Bare
`return` at EOF is invalid.

`Unit` is not a keyword; this rule is a parser special case for an identifier
whose lexeme is `Unit`.

### 7.3 If

```ebnf
ifStmt ::= "if" "(" expression ")" block [ "else" block ] ;
```

`else if` is invalid because `else` must be followed by a block.

### 7.4 While

```ebnf
whileStmt ::= "while" "(" expression ")" block ;
```

### 7.5 For

```ebnf
forStmt ::= "for" "(" identifier "in" expression "to" expression ")" block ;
```

The parser stores `for` as a desugared block:

```bred
{
    var i: Int = <initial>
    val $right_borderi: Int = <final>
    while (i <= $right_borderi) {
        <original body>
        i = i + 1
    }
}
```

The right-border variable name is `$right_border` plus the counter name. The
counter and right-border declarations are both typed as `Int`.

## 8. Expressions

```ebnf
expression     ::= logicalOr ;
logicalOr      ::= logicalAnd { "||" logicalAnd } ;
logicalAnd     ::= equality { "&&" equality } ;
equality       ::= comparison { ( "==" | "!=" ) comparison } ;
comparison     ::= additive { ( "<" | ">" | "<=" | ">=" ) additive } ;
additive       ::= multiplicative { ( "+" | "-" ) multiplicative } ;
multiplicative ::= unary { ( "*" | "/" | "%" ) unary } ;
unary          ::= ( "-" | "!" ) unary | primary ;
primary        ::= intLiteral
                 | doubleLiteral
                 | stringLiteral
                 | booleanLiteral
                 | identifier [ callSuffix | arrayAccessSuffix ]
                 | arrayInitList
                 | "(" expression ")" ;

callSuffix        ::= "(" [ expression { "," expression } ] ")" ;
arrayAccessSuffix ::= "[" expression "]" ;
```

All binary operators are left-associative. Unary operators are
right-associative and bind tighter than multiplicative operators.

| Precedence | Operators |
|------------|-----------|
| 1 | `||` |
| 2 | `&&` |
| 3 | `==`, `!=` |
| 4 | `<`, `>`, `<=`, `>=` |
| 5 | `+`, `-` |
| 6 | `*`, `/`, `%` |
| 7 | unary `-`, `!` |
| 8 | primary, calls, array access |

Function call suffixes attach only to identifiers. Array access may not be
called directly: `arr[0](1)` is invalid. Indirect calls such as `(f)()` are not
part of the grammar.

Function call arguments reject trailing commas. Array initialization lists
permit `[]` but reject `[1,]` and `[1,, 2]`.

## 9. Known Restrictions

- No semicolon syntax.
- No top-level `var`.
- No top-level statements.
- No `else if` syntax.
- No member access despite the lexer having a dot token.
- No indirect calls or first-class function syntax.
- No parser-level validation of type names.
- No mutable static array declaration syntax.
- No user-defined type declarations in the current parser grammar despite
  `ProgramRoot.types` existing in the AST.

