# bred - Grammar Reference

This document describes the syntax accepted by the current `lexer` and `parser`
modules. The `main` module is not used as a source for this document.

Source of truth:

- `lexer/src/main/kotlin/org/nnezh/lexer`
- `lexer/src/test/kotlin/org/nnezh/lexer`
- `parser/src/main/kotlin/org/nnezh/bred/ast`
- `parser/src/test/kotlin/org/nnezh/bred/ast/ParserTest.kt`
- `parser/src/main/kotlin/org/nnezh/bred/context`

Semantic checks are documented in `docs/semantic_analysis_draft.md`.
The compiler-wide type model and resolution pipeline are documented in
`docs/type_system.md`.

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
fun val var if else return while for in to true false mut type typeclass instance
```

Keywords are case-sensitive. Prefixes and longer words are identifiers:
`function`, `valuable`, `typeclasses`, `instanceOf`.

`mut` is consumed only by product type declarations: `type mut Name(...)`.
No later compiler phase currently gives `mut` a distinct semantic effect.

### 1.2 Literals

```ebnf
intLiteral     ::= digit { digit } ;
longLiteral    ::= digit { digit } "L" ;
doubleLiteral  ::= digit { digit } "." digit { digit } ;
stringLiteral  ::= '"' { stringChar | escapeSequence } '"' ;
escapeSequence ::= "\" ( "n" | "t" | "r" | '"' | "\" | "0" ) ;
booleanLiteral ::= "true" | "false" ;
```

Unsuffixed integer literals become `IntLiteralExpressionASTNode` and have
semantic type `Int`. The lexer stores their scanned numeric value as Kotlin
`Long` before the parser builds the `Int` literal AST node.

Integer literals with an uppercase `L` suffix, for example `42L`, become
`LongLiteralExpressionASTNode` and have semantic type `Long`. Values outside
the Kotlin `Long` range are lexer errors.

A dot starts a double literal only when followed by a digit. Therefore `3.`
lexes as `3` and `.`, `.5` lexes as `.` and `5`, and `1..2` lexes as
`1`, `.`, `.`, `2`.

Numbers glued to an identifier or underscore, for example `12abc`, `12Labc`,
or `1_000`, are lexer errors.

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

`;` is rejected by the lexer. The dot token is used by identifier-rooted product
field access chains.

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
               | productTypeDecl
               | typeclassDecl
               | instanceDecl ;
```

Top-level `var`, statements, assignments, and call statements are rejected.
Top-level declarations are stored in separate `ProgramRoot` lists
(`types`, `functions`, `globalVariables`, `typeClasses`, `instances`); the AST
does not preserve one combined top-level declaration order.

## 3. Types

The parser stores type syntax as `TypeRef(name, args)`. `TypeRef` is not a
semantic type; concrete `TypeSign` instances are created and returned by
`ProgramGlobalContext` / `TypeRegistry` during the resolution phase. Unknown
concrete type names are rejected during resolution, while generic names are
valid only in the generic scope that declares them.

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

Primitive names initialized by `ProgramGlobalContext` and used by tests and
built-ins are `Int`, `Long`, `Double`, `String`, `Boolean`, and `Unit`.

## 4. Product Types

```ebnf
productTypeDecl ::= "type" [ "mut" ] identifier "(" [ productField { "," productField } ] ")" ;
productField    ::= identifier ":" identifier ;
```

Examples:

```bred
type Person(name: String, age: Int)
type Address(city: String)
type Person(name: String, age: Int, address: Address)
type mut Counter(value: Int)
type Empty()
```

Product declarations are represented as `DeclareProductTypeASTNode` values in
`ProgramRoot.types`. The optional `mut` keyword is stored as
`DeclareProductTypeASTNode.isMutable`.

Field type syntax is currently a bare identifier, not the full `typeSign`
grammar. Therefore these forms are not parsed as product field declarations:

```bred
type Box(value: Array<Int>)
type Box(value: Int[])
type PairBox(value: Pair<Int, String>)
```

The parser rejects duplicate field names inside one product type declaration.
Trailing commas are invalid.

Product type names are registered by `ProgramContextCollector`. Constructor
calls use normal call syntax; during semantic analysis a call whose name matches
a product type is treated as a constructor and its argument types are matched
against the declared field types in field order.

```bred
val person: Person = Person("Nika", 7, Address("Berlin"))
```

Product field access is part of the expression grammar. Product field
assignment is part of the assignment grammar. The current compiler parses,
analyzes, lowers, and renders nested field chains whose root is an identifier,
for example `person.address.city`.

`mut` currently has no enforced semantic effect. In particular, field
assignment is checked for type compatibility, but product mutability is not
checked by the semantic analyzer.

## 5. Functions

```ebnf
functionDecl  ::= "fun" identifier genericParams? functionParams returnType? block ;
genericParams ::= "<" genericParam { "," genericParam } ">" ;
genericParam  ::= identifier [ ":" identifier { "+" identifier } ] ;
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
fun sortAndPrint<A: Ordered + Printable>(a: A) { prettyPrint(a) }
fun pair<A: Printable, B>(a: A, b: B): Pair<A, B> { return makePair(a, b) }
```

Parameter arrays use `Type[]` and are represented as a parsed element
`TypeRef` plus `FunctionArgument.isArray = true`; later phases use
`ProgramGlobalContext.arrayOf(elementType)` for the actual `Array<T>` type.
Sized parameter arrays such as `Int[3]` are not supported.

Trailing commas in generic parameter lists and function parameter lists are
invalid.

### 5.1 Synthetic Return

After parsing a function body, if the top-level block has no
`ReturnFunctionStatementAstNode`, the parser appends:

```text
ReturnFunctionStatementAstNode(expression = null, explicit = false)
```

This happens regardless of the declared return type. The semantic analyzer must
reject a non-`Unit` function whose only function-level return is synthetic.
Nested returns do not suppress the synthetic return.

## 6. Typeclasses and Instances

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

## 7. Variables and Arrays

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
val values: Array<Int>[3]

fun main(): Unit {
    val n: Int = 1
    val wide: Long = 1L
    var total: Int = 0
}
```

`Array<T>[n]` is accepted as the explicit array type form used by generic
helpers. `T[n]` is the compact element-type form. Both denote a static array
binding whose value type in semantic analysis is `Array<T>`. The same forms are
accepted for top-level immutable globals, for example `val values: Int[3]`.

Array sizes in declarations must be integer literals. `val xs: Int[n]` and
`val xs: Int[]` are invalid. Array initializers reject scalar right-hand sides,
trailing commas, and consecutive commas.

Global static arrays are emitted as C global storage. The current TAC/C lowering
supports absent global array initializers and initializer lists made of literal
elements; richer compile-time expressions are tracked in `docs/TODO.md`.

## 8. Blocks and Statements

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

### 8.1 Assignment

```ebnf
assignment  ::= lvalue "=" expression ;
lvalue      ::= identifier | arrayAccess | fieldAccess ;
arrayAccess ::= identifier "[" expression "]" ;
fieldAccess ::= identifier "." identifier { "." identifier } ;
```

The assignment parser first parses the left side as an expression and then
requires it to be either `VariableExpressionASTNode` or
`ArrayElementAccessASTNode` or `FieldAccessChainExpressionASTNode`.

The semantic analyzer checks field assignment type compatibility. It currently
checks scalar binding mutability only for `VariableExpressionASTNode` lvalues;
array element and product field assignments are not rejected merely because the
root binding or product type was declared with `val` / without `mut`.

### 8.2 Return

```ebnf
returnStmt ::= "return" [ "Unit" | expression ] ;
```

`return Unit` is represented as `expression = null, explicit = true`. A bare
`return` immediately before `}` is also represented as explicit Unit. Bare
`return` at EOF is invalid.

`Unit` is not a keyword; this rule is a parser special case for an identifier
whose lexeme is `Unit`.

### 8.3 If

```ebnf
ifStmt ::= "if" "(" expression ")" block [ "else" block ] ;
```

`else if` is invalid because `else` must be followed by a block.

### 8.4 While

```ebnf
whileStmt ::= "while" "(" expression ")" block ;
```

### 8.5 For

```ebnf
forStmt ::= "for" "(" identifier "in" expression "to" expression ")" block ;
```

The parser stores `for` as a desugared block:

```bred
{
    var i: Int = <initial>
    val _right_borderi: Int = <final>
    while (i < _right_borderi) {
        <original body>
        i = i + 1
    }
}
```

The right boundary is exclusive. For example, `for (i in 0 to size)` visits
indexes `0` through `size - 1`.

The right-border variable name is `_right_border` plus the counter name. The
counter and right-border declarations are both typed as `Int`.

## 9. Expressions

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
                 | longLiteral
                 | doubleLiteral
                 | stringLiteral
                 | booleanLiteral
                 | identifier [ callSuffix | arrayAccessSuffix | fieldAccessSuffix ]
                 | arrayInitList
                 | "(" expression ")" ;

callSuffix        ::= "(" [ expression { "," expression } ] ")" ;
arrayAccessSuffix ::= "[" expression "]" ;
fieldAccessSuffix ::= "." identifier { "." identifier } ;
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
| 8 | primary, calls, array access, field access |

Function call, array access, and field access suffixes attach only to
identifier primaries. Array access may not be called directly: `arr[0](1)` is
invalid. Field access may not be applied to arbitrary expressions:
`makePerson().age`, `(person).age`, `people[0].age`, and `person.names[0]` are
not part of the current grammar. Indirect calls such as `(f)()` are not part of
the grammar.

Function call arguments reject trailing commas. Array initialization lists
permit `[]` but reject `[1,]` and `[1,, 2]`.

## 10. Known Restrictions

- No semicolon syntax.
- No top-level `var`.
- No top-level statements.
- No `else if` syntax.
- No general member/method access beyond identifier-rooted product field
  chains.
- No indirect calls or first-class function syntax.
- No parser-level semantic validation of type names. Type syntax is accepted
  as `TypeRef`; unknown concrete names are rejected later by
  `ProgramTypeResolver`.
- No mutable static array declaration syntax.
- Product field declarations accept only bare identifier field types, not the
  full `typeSign` grammar.
- Product type declaration ordering, forward references, recursive products,
  and `mut` semantics are not fully specified.



