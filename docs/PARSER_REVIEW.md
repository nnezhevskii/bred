# Parser: ревизия построения дерева

Срез `09472ab`, 12 ноября 2026. Проверены разбор, контекст объявлений, разрешение типов и специализация шаблонов, физически находящиеся в модуле parser. Соседние документы — [analyzer](ANALYZER_REVIEW.md), [архитектура](PARSER_ANALYZER_ARCHITECTURE.md), [форма дерева](AST_REVIEW.md), [взаимодействие фаз](PARSER_ANALYZER_INTERACTION.md).

## Основание

Повторный `:parser:test :analyzer:test --offline --rerun-tasks --continue` дал parser **103/105**, analyzer **152/152**. Два падения parser относятся к известной фикстуре G-74: тесты ждут Health из старого радара, а читают Shape из текущего `examples/3ac.bred`. Дополнительные примеры выполнены через lexer → `AbstractSyntaxTreeBuilder` → collector → resolver → analyzer. Для части проверен полный путь `TACCompilerImpl.compile`. Исходники и тесты в этом разборе использованы для чтения; deliverable — документы. [Тесты фикстуры](../parser/src/test/kotlin/org/nnezh/bred/ast/Example3acParserTest.kt) · [Штатная цепочка](../tac/src/main/kotlin/org/nnezh/compiler/TACCompiler.kt)

## Подтверждённые случаи

**1. Сужение Int меняет значение исходника.** `fun f(): Int { return 2147483648 }` создаёт `IntLiteralExpressionASTNode(value=-2147483648)` и успешно проходит анализ. `val a: Int[4294967296]` создаёт массив размера 0, тоже без ошибки. В обеих ветках значение лексера Long преобразуется через `toInt()`. G-78. Проверить верхнюю границу Int, следующее значение, суффикс L, отрицательную границу с отдельным унарным минусом и размер массива. Диагностика должна появиться до потери исходной величины. [Выражения](../parser/src/main/kotlin/org/nnezh/bred/ast/AbstractSyntaxTreeExpressionParser.kt) · [Размер массива](../parser/src/main/kotlin/org/nnezh/bred/ast/VariableInitializationParser.kt)

**2. Пропущенная запятая между полями продукта принимается.** `type P(x: Int y: Long)` создаёт оба поля и проходит дальнейшие стадии. В цикле `parseProductType` запятая лишь опционально потребляется. У вариантов суммы разделитель проверяется строго. Для первой правки — одинаково проверять `,` или `)` после поля продукта. Тесты на одно поле, два поля, пропуск разделителя и обрыв на EOF. Это расхождение с формой объявления в грамматике. [TypeParser](../parser/src/main/kotlin/org/nnezh/bred/ast/TypeParser.kt) · [Грамматика](grammar.md)

**3. Подставляемое имя for захватывает пользовательское имя.**
```bred
fun f(_right_borderi: Int) {
    for (i in 0 to 3) {
        println(intToString(_right_borderi))
    }
}
```
В дереве возникает локальный `val _right_borderi: Int = 3`. Analyzer выдаёт только предупреждение о затенении и принимает программу. Чтение в теле обращается к синтетической границе цикла. Исходная программа не объявляла такую переменную. Нужна отдельная идентичность служебной привязки либо откладывание разворачивания до разрешения исходных имён. Проверить параметр, локальную переменную и вложенные циклы. [ForParser](../parser/src/main/kotlin/org/nnezh/bred/ast/ForParser.kt)

**4. Явный generic-вызов проходит в выражении, но инструкция маршрутизируется в присваивание.** `fun id<T>(x: T): T { return x } fun f() { id<Int>(1) }` завершился `Expected assignable expression but got }`. `StatementParser` узнаёт вызов по `identifier (`, а после имени здесь `<`. Отдельная проверка в парсере выражений умеет распознавать generic-call suffix. При решении поддержать инструкцию вызова с type arguments нужно использовать согласованное распознавание и дать предметную диагностику. Начать с `id<Int>(1)`, обычного `id(1)` и выражения сравнения. G-14 связан с маршрутизацией, общий синтаксис generic-call уже описан в грамматике. [StatementParser](../parser/src/main/kotlin/org/nnezh/bred/ast/StatementParser.kt) · [Парсер выражений](../parser/src/main/kotlin/org/nnezh/bred/ast/AbstractSyntaxTreeExpressionParser.kt)

**5. Повторное имя типа теряется при сборе контекста.** `type P(x: Int) type P(y: Long) fun f() {}` сохраняет два объявления в AST, проходит прямой analyzer и полный путь до TAC. Словари контекста позволяют перезаписать сведения об имени. Parser вправе сохранить оба объявления для диагностики, но стадия регистрации должна отказать до выбора полей и конструкторов. Проверить повтор продукта, повтор суммы и смешанную пару с одинаковым именем. [ProgramContextCollector](../parser/src/main/kotlin/org/nnezh/bred/context/ProgramContextCollector.kt) · [ProgramGlobalContext](../parser/src/main/kotlin/org/nnezh/bred/context/ProgramGlobalContext.kt)

## Известные ограничения и уточнения

- `fun f(Unit: Int): Int { return Unit }` разобрано как пустой явный return и затем отклонено по типу. Это соответствует задокументированному специальному разбору `return Unit` при том, что Unit остаётся identifier. Решение языка — зарезервировать это имя либо дать одинаковое правило ссылок в expression и return. [ReturnValueParser](../parser/src/main/kotlin/org/nnezh/bred/ast/ReturnValueParser.kt)
- При `id(1 < 2)` тип аргумента во время специализации определяется как тип левого операнда, хотя выражение имеет тип Boolean. Пример из прежней ревизии отклоняется позже как вызов `id_Int_Int` с Boolean. Это зона ответственности специализатора, который сейчас живёт в parser. [Разбор](ARCHITECTURE_REVIEW.md)
- `TokensContext.top(offset)` индексирует список без защиты; обычный контракт lexer содержит EOF, а публичный builder принимает произвольный список токенов. Здесь риск по API, сбой на обрезанном списке в этой задаче не воспроизводился. Явно описать вход либо проверять терминальный EOF. Глубокая рекурсия выражений и цепочек полей также требует замера до назначения лимита. [TokensContext](../parser/src/main/kotlin/org/nnezh/bred/ast/TokensContext.kt)

**Первая короткая задача:** отрицательный тест `type P(x: Int y: Long)` рядом с положительным тестом двух полей. Отдельная более содержательная задача — устранить захват имени в for с проверкой привязки из исходной программы.
