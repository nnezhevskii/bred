# bred — Grammar Reference

Technical reference for the **syntax accepted by the current parser implementation**. This document is derived from the lexer, AST parsers, unit tests, and example files — not from an idealized language design.

**Scope:** lexical and syntactic rules only. No type checker or runtime semantics are described here.

**Pipeline:** source (`.bred`) → `Lexer` → token stream → `ProgramParser` / nested parsers → AST (`org.nnezh.ast`).

**Sources:** see [Sources](#sources) at the end.

---

## Table of contents

1. [Lexical structure](#1-lexical-structure)
2. [Program structure](#2-program-structure)
3. [Declarations](#3-declarations)
4. [Blocks and statements](#4-blocks-and-statements)
5. [Expressions](#5-expressions)
6. [Function calls](#6-function-calls)
7. [Type names](#7-type-names)
8. [Whitespace and newlines](#8-whitespace-and-newlines)
9. [Syntax errors and limitations](#9-syntax-errors-and-limitations)
10. [Open questions / inconsistencies](#10-open-questions--inconsistencies)
11. [Checklist for parser tests](#11-checklist-for-parser-tests)
12. [Sources](#sources)

---

## 1. Lexical structure

The lexer is hand-written (`Lexer.kt`). It skips whitespace and comments, emits tokens, and appends a single `EOF` token at the end.

### 1.1 Whitespace

**Rule:** any character for which `Char.isWhitespace()` is true is skipped and produces no token.

**Examples:**

```bred
val x = 1
val  y  =  2
```

```bred
fun f(): Int {
    val a = 1
}
```

**Notes:**

- Positions are 1-based (`line`, `column`). A newline resets `column` to 1.
- Whitespace inside string literals is significant and is not skipped.

**Implementation:** `Lexer.skipWhitespaceAndComments()`.

---

### 1.2 Comments

Comments produce no tokens.

```ebnf
lineComment  ::= '//' { anyCharExceptNewline } ;
blockComment ::= '/*' { anyChar } '*/' ;
```

**Examples:**

```bred
// this is ignored
val x = 1 /* inline */ + 2
/* multi
   line
   comment */
```

**Notes:**

- Block comments are **not nested**; the first `*/` closes the comment.
- An unclosed block comment at EOF → `LexerError.UnterminatedBlockComment`.
- `/` as division is recognized only when it does not start a comment (comments are skipped first).

**Implementation:** `Lexer.skipLineComment()`, `Lexer.skipBlockComment()`.

---

### 1.3 Identifiers

```ebnf
identifier ::= ( letter | '_' ) { letter | digit | '_' } ;
```

Where `letter` / `digit` follow Kotlin `Char.isLetter()` / `Char.isLetterOrDigit()` (Unicode-aware).

**Examples:**

```bred
x
_private
readInt
x1
function
```

**Notes:**

- After scanning, the text is looked up in the keyword table (`Token.Keyword.byText`). Exact match → keyword token; otherwise → `Identifier`.
- Prefixes of keywords remain identifiers: `function`, `valuable`, `iffy` are **not** keywords.

**Implementation:** `Lexer.scanIdentifierOrKeyword()`, `LexerTest.identifiers that look like keywords are still identifiers`.

---

### 1.4 Keywords

Reserved words (12). Each maps to a `Token.Keyword` variant.

| Lexeme   | Token                    |
|----------|--------------------------|
| `fun`    | `Token.Keyword.Fun`      |
| `val`    | `Token.Keyword.Val`      |
| `var`    | `Token.Keyword.Var`      |
| `if`     | `Token.Keyword.If`       |
| `else`   | `Token.Keyword.Else`     |
| `return` | `Token.Keyword.Return`   |
| `while`  | `Token.Keyword.While`    |
| `for`    | `Token.Keyword.For`      |
| `in`     | `Token.Keyword.In`       |
| `to`     | `Token.Keyword.To`       |
| `true`   | `Token.Keyword.True`     |
| `false`  | `Token.Keyword.False`    |

**Examples:**

```bred
fun val if else return while for in to true false
```

**Notes:**

- `true` and `false` are keywords, not separate literal token types. The expression parser maps them to `BooleanLiteralExpressionNode`.
- Several keywords are recognized by the lexer but **not** by the statement/program parsers (`var`, `return`). See [§10](#10-open-questions--inconsistencies).

**Implementation:** `Token.Keyword.byText`.

---

### 1.5 Numeric literals

#### Integer

```ebnf
intLiteral ::= digit { digit } ;
```

Parsed as `Long` via `toLongOrNull()`. Overflow → `LexerError.InvalidNumber`.

**Examples:** `0`, `42`, `1000000`

#### Double

```ebnf
doubleLiteral ::= digit { digit } '.' digit { digit } ;
```

A fractional part applies **only when a digit follows the dot**.

**Examples:**

```bred
3.14
2.0
0.5
```

**Counter-example (important):**

```bred
3.
```

Tokenizes as `IntLiteral(3)` + `Punctuation.Dot` — **not** a double.

**Notes:**

- Scientific notation (`1e10`), hex/octal prefixes — **not supported**.
- Sign is not part of the literal; unary `-` is a separate operator.
- A number immediately followed by a letter or `_` (e.g. `12abc`, `3.14abc`) → `LexerError.InvalidNumber`.

**Implementation:** `Lexer.scanNumber()`, `LexerTest`.

---

### 1.6 String literals

```ebnf
stringLiteral ::= '"' { stringChar | escapeSequence } '"' ;
escapeSequence ::= '\' ( 'n' | 't' | 'r' | '"' | '\' | '0' ) ;
```

**Examples:**

```bred
""
"hello"
"line\n\t\"quote\"\\"
```

**Notes:**

- Only double quotes delimit strings.
- Unterminated string (EOF, raw newline inside, trailing `\`) → `LexerError.UnterminatedString`.
- Unknown escape (e.g. `\q`) → `LexerError.UnknownEscape`.
- `value` in the token is decoded; `lexeme` keeps the raw source including quotes.

**Implementation:** `Lexer.scanString()`, `Lexer.decodeEscape()`.

---

### 1.7 Operators

| Lexeme | Token                 | Arity / role        |
|--------|-----------------------|---------------------|
| `+`    | `Operator.Plus`       | binary / unary `-` sibling |
| `-`    | `Operator.Minus`      | binary / unary      |
| `*`    | `Operator.Star`       | binary              |
| `/`    | `Operator.Slash`      | binary              |
| `%`    | `Operator.Percent`    | binary              |
| `=`    | `Operator.Assign`     | assignment only     |
| `==`   | `Operator.Eq`         | binary              |
| `!=`   | `Operator.Neq`        | binary              |
| `<`    | `Operator.Lt`         | binary              |
| `>`    | `Operator.Gt`         | binary              |
| `<=`   | `Operator.Le`         | binary              |
| `>=`   | `Operator.Ge`         | binary              |
| `&&`   | `Operator.And`        | binary              |
| `\|\|` | `Operator.Or`         | binary              |
| `!`    | `Operator.Not`        | unary               |

Multi-character operators are recognized greedily (`==` before `=`, `<=` before `<`, etc.).

**Notes:**

- Lone `&` or `\|` → `LexerError.UnexpectedCharacter` with hint `did you mean '&&'?` / `'||'?`.
- `=` in expressions is always `Assign`; equality is `==`.

**Implementation:** `Lexer.scanOperatorOrPunctuation()`.

---

### 1.8 Punctuation

| Lexeme | Token                    |
|--------|--------------------------|
| `(`    | `Punctuation.LParen`     |
| `)`    | `Punctuation.RParen`     |
| `{`    | `Punctuation.LBrace`     |
| `}`    | `Punctuation.RBrace`     |
| `,`    | `Punctuation.Comma`      |
| `:`    | `Punctuation.Colon`      |
| `;`    | `Punctuation.Semicolon`  |
| `.`    | `Punctuation.Dot`        |

**Examples:**

```bred
(){},:;.
```

**Notes:**

- `;` is tokenized but **not consumed by any parser** — statement terminators are **not confirmed** in the grammar. See [§10](#10-open-questions--inconsistencies).
- `.` as member access / field access — **not confirmed**. In practice `.` appears as a separate token after integers without a fractional part (`3.`).

**Implementation:** `Token.Punctuation`, `LexerTest.punctuation is recognized`.

---

### 1.9 End of input

```ebnf
EOF ::= /* synthetic token appended once after the last source token */ ;
```

Empty or whitespace-only input produces only `EOF`.

**Implementation:** `Lexer.tokenize()`.

---

### 1.10 Lexical errors

| Error | Typical cause |
|-------|----------------|
| `UnexpectedCharacter` | `@`, lone `&`, unrecognized symbol |
| `UnterminatedString` | missing closing `"`, newline inside string |
| `UnterminatedBlockComment` | missing `*/` |
| `UnknownEscape` | `\q` and similar |
| `InvalidNumber` | `12abc`, out-of-range integer |

Errors are returned as `Either<LexerError, List<Token>>`; the lexer does not throw.

**Implementation:** `LexerError.kt`.

---

## 2. Program structure

A program is a sequence of top-level declarations followed by EOF.

```ebnf
program ::= { topLevelDecl } EOF ;

topLevelDecl ::= functionDecl
               | globalValDecl ;
```

**Example:**

```bred
fun min(a: Int, b: Int): Int { }

val Pi: Double = 3.1417
```

**Notes:**

- Only `fun` and `val` are allowed at the top level.
- Any other token at program start → `Expected function or constant declaration`.
- Nested functions, statements, and assignments at file scope — **not supported**.

**Implementation:** `ProgramParser.kt`, `ProgramParserTest`.

---

## 3. Declarations

### 3.1 Function declaration

```ebnf
functionDecl ::= 'fun' identifier '(' [ param { ',' param } ] ')' ':' returnTypeName block ;

param          ::= identifier ':' typeName ;
typeName       ::= identifier ;   (* validated — see §7 *)
returnTypeName ::= identifier ;   (* NOT validated via Type.parseOrNull *)
```

**Example:**

```bred
fun max(a: Int, b: Int): Int {
    if (a > b) {
        println("a")
    }
}
```

**Notes:**

- Parameter list may be empty: `fun main(): Unit { }`.
- Trailing commas in the parameter list are **not** allowed (unlike function call arguments).
- Parameter types must be one of `Int`, `String`, `Double`, `Boolean` (see [§7](#7-type-names)).
- Return type is stored as a raw `String` in `DeclareFunctionASTNode.resultType` — any identifier is accepted syntactically (e.g. `Unit`, `Foo`).
- Function body is a single `block` (see [§4](#4-blocks-and-statements)).

**Implementation:** `FunctionParser.kt`, `FunctionParserTest`.

---

### 3.2 Immutable variable declaration (`val`)

```ebnf
globalValDecl ::= 'val' identifier ':' typeName '=' expression ;
valDecl       ::= globalValDecl ;   (* same syntax inside blocks *)
```

**Example:**

```bred
val Pi: Double = 3.1417
```

```bred
fun main(): Unit {
    val n: Int = 42
}
```

**Notes:**

- Type annotation is **required**; `val x = 1` — **not supported**.
- `typeName` must resolve via `Type.parseOrNull` (see [§7](#7-type-names)).
- `var` declarations — **not confirmed** at surface syntax (no parser). `MutableVariableInitializationASTNode` exists only as for-loop desugaring output.

**Implementation:** `ImmutableInitializationParser.kt`, `ImmutableInitializationParserTest`.

---

## 4. Blocks and statements

### 4.1 Block

```ebnf
block ::= '{' { statement } '}' ;
```

**Example:**

```bred
{
    val x: Int = 1
    println(x)
}
```

**Notes:**

- Zero statements in a block is valid: `{ }`.
- Statements are parsed until `}`; no separator token between statements.
- Unclosed block → `Unexpected EOF` or `Expected end of block`.

**Implementation:** `BlockParser.kt`, `BlockParserTest`.

---

### 4.2 Statement (dispatch)

```ebnf
statement ::= valDecl
            | assignment
            | callStmt
            | ifStmt
            | whileStmt
            | forStmt ;
```

**Dispatch rules** (`StatementParser.kt`) — not expressible as pure EBNF:

| First token(s)              | Parser route        |
|-----------------------------|---------------------|
| `val`                       | `ImmutableInitializationParser` |
| `if`                        | `IfParser`          |
| `while`                     | `WhileParser`       |
| `for`                       | `ForParser`         |
| `identifier` `(`            | `CallStatementParser` |
| `identifier` (not followed by `(`) | `AssignParser` |
| anything else               | parse error         |

**Notes:**

- Token streams always end with `EOF` (appended by `Lexer.tokenize()`). A lone `identifier` immediately before `EOF` is rejected with `Unexpected end of file` at routing time — neither assign nor call parser is invoked.
- `return expr` — **not confirmed** (keyword lexed, no parser/AST node).

**Implementation:** `StatementParser.kt`, `StatementParserTest`.

---

### 4.3 Assignment

```ebnf
assignment ::= identifier '=' expression ;
```

**Example:**

```bred
x = 42
counter = counter + 1
```

**Notes:**

- Only a plain identifier on the left; no `a[i]` or destructuring.
- `==` is not assignment.

**Implementation:** `AssignParser.kt`, `AssignParserTest`.

---

### 4.4 Call statement

```ebnf
callStmt ::= expression ;
```

At statement level, routing reaches `CallStatementParser` only when the statement begins with `identifier '('`. The parser wraps the parsed expression in `CallFunctionStatementASTNode`.

**Examples:**

```bred
println()
println(a, 42)
foo()
```

**Notes:**

- A statement starting with `(`, a literal, or a unary operator — **rejected** at `StatementParser` dispatch (not routed to call parser).
- `CallStatementParser` itself delegates entirely to the expression parser; see [§6](#6-function-calls).

**Implementation:** `CallStatementParser.kt`, `CallStatementParserTest`.

---

### 4.5 `if` / `else`

```ebnf
ifStmt ::= 'if' '(' expression ')' block [ 'else' block ] ;
```

**Example:**

```bred
if (x > y) {
    println("greater")
} else {
    println("less or equal")
}
```

**Notes:**

- Parentheses around the condition are **required**.
- `else` branch is optional.
- `else if` chains — **not confirmed** (would require `else` followed by `if` as a single statement; not implemented).

**Implementation:** `IfParser.kt`, `IfParserTest`.

---

### 4.6 `while`

```ebnf
whileStmt ::= 'while' expression block ;
```

**Examples:**

```bred
while true { }
while loop() > 1 { }
while (x < 10) { }
```

**Notes:**

- Parentheses around the condition are **not required** (unlike `if`).
- Parentheses may appear as part of a parenthesized subexpression.

**Implementation:** `WhileParser.kt`, `WhileParserTest`.

---

### 4.7 `for`

```ebnf
forStmt ::= 'for' '(' identifier 'in' expression 'to' expression ')' block ;
```

**Example:**

```bred
for (i in 0 to 10) {
    println(i)
}
```

**Notes:**

- Header parentheses are **required**.
- Only the `in … to …` range form is supported; C-style `for (init; cond; step)` — **not confirmed**.
- Bounds expressions may be any valid `expression` syntactically; no static type check is performed.

#### For-loop desugaring (implementation note)

Surface `for` is not stored as-is. `ForParser` builds `ForStatementASTNode` containing a desugared block:

```ebnf
(* conceptual desugaring — not surface syntax *)
{
    var counter : Int = <startExpr> ;
    while ( counter <= <endExpr> ) {
        <original block statements> ;
        counter = counter + 1 ;
    }
}
```

- Counter variable type is always `Int` in the synthesized `MutableVariableInitializationASTNode`.
- `<=` and `+` operators reuse the **source position of the `to` keyword** as synthetic locations.
- Increment statement is appended after the user's block statements inside the while body.

**Implementation:** `ForParser.kt`, `ForParserTest`.

---

## 5. Expressions

```ebnf
expression     ::= logicalOr ;

logicalOr      ::= logicalAnd { '||' logicalAnd } ;
logicalAnd     ::= equality { '&&' equality } ;
equality       ::= comparison { ( '==' | '!=' ) comparison } ;
comparison     ::= additive { ( '<' | '>' | '<=' | '>=' ) additive } ;
additive       ::= multiplicative { ( '+' | '-' ) multiplicative } ;
multiplicative ::= unary { ( '*' | '/' | '%' ) unary } ;
unary          ::= ( '-' | '!' ) unary
                 | primary ;
primary        ::= intLiteral
                 | doubleLiteral
                 | stringLiteral
                 | 'true'
                 | 'false'
                 | identifier [ callSuffix ]
                 | '(' expression ')' ;

callSuffix     ::= '(' [ expression { ',' expression } ] ')' ;
```

### 5.1 Operator precedence and associativity

From lowest to highest binding strength:

| Precedence | Operators        | Associativity   |
|------------|------------------|-----------------|
| 1 (lowest) | `\|\|`           | left            |
| 2          | `&&`             | left            |
| 3          | `==` `!=`        | left            |
| 4          | `<` `>` `<=` `>=`| left            |
| 5          | `+` `-`          | left            |
| 6          | `*` `/` `%`      | left            |
| 7          | `-` `!` (unary)  | right           |
| 8 (highest)| primary, calls   | —               |

**Examples:**

```bred
a + b * c          (* (* b c) — multiplication binds tighter *)
a || b && c        (* (|| a (&& b c)) *)
3 + 5 + x          (* left-associative addition *)
-x + 1             (* unary minus binds tighter than + *)
!flag              (* unary not *)
```

**Implementation:** `AbstractSyntaxTreeExpressionParser.kt`, `AbstractSyntaxTreeExpressionParserTest`.

---

## 6. Function calls

Function calls appear inside expressions at the **primary** level, only when an identifier is immediately followed by `(`.

```ebnf
call ::= identifier '(' [ expression { ',' expression } ] ')' ;
```

**Examples:**

```bred
f()
g(1)
h(1, 2, 3)
max(a, min(b, c))
f(a + 1, b * 2)
```

**Notes:**

- Empty argument list: `f()`.
- Trailing comma in argument list (e.g. `max(3,)`) → parse error.
- Missing separator `max(3 5)` → parse error.
- Indirect calls `(getFn())()` — **not confirmed** (call suffix attaches only to identifier primary).
- Method call / field access `obj.method()` — **not confirmed** (no `.` in expression grammar).

**Implementation:** `AbstractSyntaxTreeExpressionParser.parseCall()`, `AbstractSyntaxTreeExpressionParserTest`, `CallStatementParserTest`.

---

## 7. Type names

The runtime type enum (`Types.kt`) contains exactly four types:

| Name      | `Type` object   |
|-----------|-----------------|
| `Int`     | `IntType`       |
| `String`  | `StringType`    |
| `Double`  | `DoubleType`    |
| `Boolean` | `BoolType`      |

**Where validated (`Type.parseOrNull`):**

- `val` declaration type annotations
- function parameter types

**Where NOT validated:**

- function return type (`returnTypeName` stored as raw string)

**Examples:**

```bred
val x: Int = 1              (* OK *)
fun f(a: Foo): Int { }      (* parse error: Invalid type Foo — param *)
fun f(): Unit { }           (* OK syntactically; Unit not in Type enum *)
fun f(): Foo { }            (* OK syntactically; Foo not validated *)
```

**Implementation:** `Types.kt`, `ImmutableInitializationParser.kt`, `FunctionParser.kt`.

---

## 8. Whitespace and newlines

- Whitespace and comments may appear between any two tokens.
- Newlines are not statement terminators.
- Multiple statements in a block are written sequentially inside `{ }` without `;`:

```bred
{
    val a: Int = 1
    val b: Int = 2
    println(a + b)
}
```

- String literals cannot contain raw newlines; use `\n`.

**Implementation:** `Lexer.kt`, `BlockParser.kt`.

---

## 9. Syntax errors and limitations

### Parser-reported errors (representative)

| Situation | Message pattern |
|-----------|-------------------|
| Top-level non-declaration | `Expected function or constant declaration` |
| Unknown `val`/param type | `Invalid type <name> at <position>` |
| Malformed block | `Expected begin/end of block` |
| Missing expression | `Expected expression but got …` |
| Bad function call | `Expected ',' or ')'` |
| Unexpected statement start | `Didn't expect <token>` |

### Confirmed limitations

- No `return` statement parser.
- No surface `var` declaration parser.
- No semicolon-separated statements (token exists, unused).
- No member access (`.` not in expression grammar).
- No array indexing, generics, or user-defined types in the parser.
- No string interpolation.
- Examples in `examples/` are **not** fully valid programs for the current parser (see [§10](#10-open-questions--inconsistencies)).

---

## 10. Open questions / inconsistencies

| # | Issue | Evidence |
|---|-------|----------|
| 1 | `return` is lexed but not parsed | `Token.Keyword.Return`; no `ReturnStatementASTNode`, no parser |
| 2 | `var` is lexed but not parsed at surface | `Token.Keyword.Var`; `MutableVariableInitializationASTNode` only from `ForParser` desugar |
| 3 | `Unit` used in examples, absent from `Type` | `examples/simple.bred`; `DeclareFunctionASTNode.resultType` is `String` |
| 4 | Example files contain invalid syntax | `max.bred`: `return`, `val z = …`, `fun main()` without `: Type`; `sandbox.bred`: `fun main()` without return type; `simple.bred`: `val z = …`, `return`-like patterns absent but other invalid forms present |
| 5 | `if` requires `()`, `while` does not | `IfParser.kt` vs `WhileParser.kt` |
| 6 | `;` token unused | `Token.Punctuation.Semicolon`; no parser consumes it |
| 7 | Return type vs param type validation asymmetry | `FunctionParser` stores return type as unchecked string |
| 8 | For-loop counter always `Int` in desugar regardless of bound types | `ForParser.kt` hardcodes `Type.IntType` |
| 9 | Package split: parsers vs AST nodes | `org.nnezh.org.nnezh.ast` vs `org.nnezh.ast` |
| 10 | `FunctionArgsASTNode` is not an `ASTNode` | `ASTNode.kt` |
| 11 | Call statement vs expression routing mismatch | `CallStatementParser` accepts any expression; `StatementParser` only routes `identifier '('` |

See also [`docs/TODO.md`](TODO.md) for actionable follow-ups.

---

## 11. Checklist for parser tests

Current suite: **13 test files**, ~**280** test methods in `src/test/kotlin/`.

### Covered

| Area | Test file | Key cases |
|------|-----------|-----------|
| Lexer | `LexerTest.kt` | empty input, keywords, identifiers, int/double/string, operators, punctuation, comments, positions, lexical errors |
| Source I/O | `SourceReaderTest.kt` | read `.bred` files |
| Expressions | `AbstractSyntaxTreeExpressionParserTest.kt` | precedence, associativity, unary, calls, grouping, literals, negative malformed |
| Program | `ProgramParserTest.kt` | empty program, fun/val routing, integration, top-level errors |
| Functions | `FunctionParserTest.kt` | params, return type, block, commas, invalid types |
| `val` init | `ImmutableInitializationParserTest.kt` | type/name/assign, invalid types, integration |
| Blocks | `BlockParserTest.kt` | empty/multiple statements, braces, EOF inside block |
| Statements | `StatementParserTest.kt` | routing to all statement kinds, integration, dispatch errors |
| Assign | `AssignParserTest.kt` | name, expression delegation, malformed |
| Call stmt | `CallStatementParserTest.kt` | delegation, wrapping, integration calls, errors |
| `if` | `IfParserTest.kt` | condition parens, else, blocks, errors |
| `while` | `WhileParserTest.kt` | condition variants, block, errors |
| `for` | `ForParserTest.kt` | header, desugaring shape, synthetic positions, errors |

### Gaps / recommended additions

- [ ] **End-to-end:** parse a fully valid multi-function program file from disk (no current example parses end-to-end).
- [ ] **Function return type `Foo`:** accepted syntactically, never validated — document/ test explicitly.
- [ ] **Semicolon between statements:** confirm tokens are ignored or cause unexpected-token errors.
- [ ] **Statement `(f)()` or `1 + foo()`:** confirm rejection at `StatementParser` dispatch.
- [ ] **Lexer keywords `for`, `in`, `to`:** explicit keyword recognition test (subset tested in `keywords are recognized`).
- [ ] **`else if` chain:** confirm rejection (not implemented).
- [ ] **`return` / `var`:** add tests when parsers are implemented.
- [x] **`StatementParser` routing at EOF:** lone `identifier` before `EOF` returns `ASTError` (`StatementParserTest`).
- [ ] **Regression:** `examples/*.bred` — either fix examples or add tests asserting expected parse failures per construct.

---

## Sources

### Lexer

- `src/main/kotlin/org/nnezh/lexer/Lexer.kt`
- `src/main/kotlin/org/nnezh/lexer/Token.kt`
- `src/main/kotlin/org/nnezh/lexer/LexerError.kt`
- `src/test/kotlin/org/nnezh/lexer/LexerTest.kt`

### AST

- `src/main/kotlin/org/nnezh/ast/ASTNode.kt`
- `src/main/kotlin/org/nnezh/Types.kt`

### Parsers

- `src/main/kotlin/org/nnezh/ast/ProgramParser.kt`
- `src/main/kotlin/org/nnezh/ast/FunctionParser.kt`
- `src/main/kotlin/org/nnezh/ast/ImmutableInitializationParser.kt`
- `src/main/kotlin/org/nnezh/ast/BlockParser.kt`
- `src/main/kotlin/org/nnezh/ast/StatementParser.kt`
- `src/main/kotlin/org/nnezh/ast/AssignParser.kt`
- `src/main/kotlin/org/nnezh/ast/CallStatementParser.kt`
- `src/main/kotlin/org/nnezh/ast/IfParser.kt`
- `src/main/kotlin/org/nnezh/ast/WhileParser.kt`
- `src/main/kotlin/org/nnezh/ast/ForParser.kt`
- `src/main/kotlin/org/nnezh/ast/AbstractSyntaxTreeExpressionParser.kt`
- `src/main/kotlin/org/nnezh/ast/ParserFactory.kt`
- `src/main/kotlin/org/nnezh/ast/AstErrorBuilder.kt`

### Tests

All files under `src/test/kotlin/org/nnezh/ast/` and `src/test/kotlin/org/nnezh/lexer/`.

### Examples (non-normative)

- `examples/simple.bred` — mixed valid/invalid constructs; stress sample
- `examples/max.bred` — partial valid fragments; contains `return`, untyped `val`, missing return types
- `examples/sandbox.bred` — `fun main() { }` (missing `: Type` on function)
