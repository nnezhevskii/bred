# Возвращение в Bred

Открой [isPositive.bred](../tac/src/test/resources/isPositive.bred) и найди `if (x > 0)`. В методе `isPositive` видно, как сравнение `GT` передаёт условие в `JMP_IF_NOT` и на какую метку ведёт переход. [Тест последовательности](../tac/src/test/kotlin/org/nnezh/lltag/TACInstructionSequenceTest.kt)

Из корня репозитория в PowerShell можно запустить проверки этого примера.

```powershell
$env:JAVA_HOME = 'C:\Users\Nikita\.jdks\azul-25.0.3'
.\gradlew.bat :tac:test --tests '*TACInstructionSequenceTest.isPositive'
```

Через 5–10 минут можно найти в тесте переход для одной ветки и проверить результат запуска.

## Если хочется сделать одно изменение

Выбери любую карточку. Оценки примерные, каждая карточка заканчивается самостоятельным результатом.

| Сколько сил | Задача | Когда можно остановиться |
|---|---|---|
| 5–10 минут, документация | G-85. Открой [пример фигур](../examples/3ac.bred) и [таблицу примеров README](../README.md). Исправь только описание этого файла. | По одной строке README понятно, что пример показывает тип-сумму и извлечение полей в `when`. |
| 10–15 минут, один тест | G-57. В [LexerTest](../lexer/src/test/kotlin/org/nnezh/lexer/LexerTest.kt) найди `keywords are recognized as keyword tokens`. Добавь `type` во вход и `Token.Keyword.Type` на ту же позицию в ожидаемом списке. | `.\gradlew.bat :lexer:test --tests '*LexerTest'` проходит, тест явно различает `type` и `typeclass`. |
| 10–20 минут, видимый результат | G-84. В [square](../examples/3ac.bred) прочитай ветку `Rectangle` и строку `return`. Исправь результат. При настроенном MSVC собери из корня командой `.\gradlew.bat run --args="examples/3ac.bred build/out/shapes.exe true"` и запусти `.\build\out\shapes.exe`. В конце введи число. | Для прямоугольника программа печатает `8.000000`, для квадрата — `16.000000`. |
| 20–40 минут, восстановить тесты | G-74. Запусти `.\gradlew.bat :parser:test --tests '*Example3acParserTest'`. Сравни фактический `Shape` с ожидаемым `Health`. Посмотри полный старый исходник командой `git show e90d4f4:examples/3ac.bred`. Сохрани точный контракт радара в отдельной тестовой фикстуре и переведи на неё parser, analyzer и root-тесты. Для root учти, что stdlib уже объявляет `Maybe`. | Обе parser-проверки и root-проверка payload зелёные, семантический тест действительно читает радар. Финальная команда — `.\gradlew.bat test --continue`. |

Если захочется следующей задачи с кодом компилятора, у **G-78** узкая граница: сравни значения `2147483647` и `2147483648` в AST. Для отдельной сессии достаточно написать один падающий тест на потерю значения. [Карточка и места в коде](AUDIT.md#g-78-int-литерал-может-тихо-поменять-значение)
