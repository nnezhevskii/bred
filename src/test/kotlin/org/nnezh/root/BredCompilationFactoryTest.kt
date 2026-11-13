package org.nnezh.root

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.nnezh.BredToCCompilerFactory
import org.nnezh.CCompilerInvocationResult
import org.nnezh.CExecutableBuilder
import org.nnezh.StandardLibraryProvider
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class BredCompilationFactoryTest {
    @Test
    fun `bred compiler returns c lines for valid source file`(@TempDir dir: Path) {
        val source = dir.resolve("main.bred")
        source.writeText("fun main(): Unit { }")

        val result = BredToCCompilerFactory().create().compile(source)

        result.fold(
            ifLeft = { errors -> error("expected C code, got: $errors") },
            ifRight = { cLines ->
                assertTrue(cLines.any { it.contains("int main") }, "expected main function in C output: $cLines")
            },
        )
    }

    @Test
    fun `bred compiler injects stdlib functions before user program`(@TempDir dir: Path) {
        val source = dir.resolve("uses-stdlib.bred")
        source.writeText(
            """
            fun main(): Unit {
                printInt(42)
            }
            """.trimIndent(),
        )

        val result = BredToCCompilerFactory().create().compile(source)

        result.fold(
            ifLeft = { errors -> error("expected C code, got: $errors") },
            ifRight = { cLines ->
                assertTrue(
                    cLines.any { it.matches(Regex("""void printInt\(int param_value_\d+\);""")) },
                    "expected stdlib function prototype in C output: $cLines",
                )
                assertTrue(
                    cLines.any { it.matches(Regex("""void printInt\(int param_value_\d+\) \{""")) },
                    "expected stdlib function body in C output: $cLines",
                )
                assertTrue(
                    cLines.any { it.contains("intToString(") },
                    "expected stdlib function to call extern intToString: $cLines",
                )
            },
        )
    }

    @Test
    fun `user function cannot redefine stdlib signature`(@TempDir dir: Path) {
        val source = dir.resolve("duplicate-stdlib.bred")
        source.writeText(
            """
            fun printInt(value: Int): Unit { }

            fun main(): Unit { }
            """.trimIndent(),
        )

        val result = BredToCCompilerFactory().create().compile(source)

        result.fold(
            ifLeft = { errors ->
                val message = errors.joinToString("\n")
                assertTrue(message.contains("Semantic error REDEFINE_FUNCTION"), message)
                assertTrue(message.contains("printInt"), message)
            },
            ifRight = { cLines -> error("expected stdlib redefinition error, got C code: $cLines") },
        )
    }

    @Test
    fun `missing stdlib returns readable compiler error`(@TempDir dir: Path) {
        val source = dir.resolve("main.bred")
        source.writeText("fun main(): Unit { }")
        val provider = StandardLibraryProvider(stdlibPath = dir.resolve("missing-stdlib.bred"))

        val result = BredToCCompilerFactory(standardLibraryProvider = provider).create().compile(source)

        result.fold(
            ifLeft = { errors ->
                val message = errors.joinToString("\n")
                assertTrue(message.contains("Standard library error:"), message)
                assertTrue(message.contains("missing-stdlib.bred"), message)
            },
            ifRight = { cLines -> error("expected stdlib read error, got C code: $cLines") },
        )
    }

    @Test
    fun `invalid stdlib returns readable compiler error`(@TempDir dir: Path) {
        val source = dir.resolve("main.bred")
        val stdlib = dir.resolve("stdlib.bred")
        source.writeText("fun main(): Unit { }")
        stdlib.writeText("fun broken(: Unit { }")
        val provider = StandardLibraryProvider(stdlibPath = stdlib)

        val result = BredToCCompilerFactory(standardLibraryProvider = provider).create().compile(source)

        result.fold(
            ifLeft = { errors ->
                val message = errors.joinToString("\n")
                assertTrue(message.contains("Standard library error:"), message)
                assertTrue(message.contains("Parser error"), message)
            },
            ifRight = { cLines -> error("expected stdlib parser error, got C code: $cLines") },
        )
    }

    @Test
    fun `bred compiler emits global static arrays`(@TempDir dir: Path) {
        val source = dir.resolve("global-array.bred")
        source.writeText(
            """
            val values: Int[3] = [1, 2, 3]

            fun sum(arr: Int[], size: Int): Int {
                return arr[0] + arr[1] + arr[2]
            }

            fun main(): Unit {
                val total: Int = sum(values, 3)
            }
            """.trimIndent(),
        )

        val result = BredToCCompilerFactory().create().compile(source)

        result.fold(
            ifLeft = { errors -> error("expected C code, got: $errors") },
            ifRight = { cLines ->
                val declaration = Regex("""int local_values_\d+\[3\] = \{1, 2, 3\};""")
                val declarationIndex = cLines.indexOfFirst { it.matches(declaration) }
                assertTrue(
                    declarationIndex >= 0,
                    "expected global array declaration in C output: $cLines",
                )
                assertTrue(
                    declarationIndex < cLines.indexOfFirst { it.startsWith("int main(") },
                    "expected global array declaration before main: $cLines",
                )
            },
        )
    }

    @Test
    fun `bred compiler emits uninitialized global static arrays`(@TempDir dir: Path) {
        val source = dir.resolve("global-array-empty.bred")
        source.writeText(
            """
            val values: Int[2]

            fun main(): Unit {
                val first: Int = values[0]
            }
            """.trimIndent(),
        )

        val result = BredToCCompilerFactory().create().compile(source)

        result.fold(
            ifLeft = { errors -> error("expected C code, got: $errors") },
            ifRight = { cLines ->
                assertTrue(
                    cLines.any { it == "int local_values_18[2];" },
                    "expected uninitialized global array declaration in C output: $cLines",
                )
            },
        )
    }

    @Test
    fun `bred compiler emits long arithmetic and longToString call`(@TempDir dir: Path) {
        val source = dir.resolve("long-math.bred")
        source.writeText(
            """
            fun main(): Unit {
                val a: Long = 40L
                val b: Long = 2L
                val total: Long = (a + b - 10L) / 2L % 9L
                val text: String = longToString(total)
                println(text)
            }
            """.trimIndent(),
        )

        val result = BredToCCompilerFactory().create().compile(source)

        result.fold(
            ifLeft = { errors -> error("expected C code, got: $errors") },
            ifRight = { cLines ->
                assertTrue(cLines.any { it.contains("long long") }, "expected long long declarations: $cLines")
                assertTrue(cLines.any { it.contains("+") && it.contains("=") }, "expected Long addition: $cLines")
                assertTrue(cLines.any { it.contains("-") && it.contains("=") }, "expected Long subtraction: $cLines")
                assertTrue(cLines.any { it.contains("/") && it.contains("=") }, "expected Long division: $cLines")
                assertTrue(cLines.any { it.contains("%") && it.contains("=") }, "expected Long modulo: $cLines")
                assertTrue(cLines.any { it.contains("longToString(") }, "expected longToString call: $cLines")
            },
        )
    }

    @Test
    fun `bred compiler emits product and sum type c code`(@TempDir dir: Path) {
        val source = dir.resolve("product-sum.bred")
        source.writeText(
            """
            type mut Counter(value: Int)
            type MaybeInt = Nothing | Just<Int>

            fun main(): Unit {
                val counter: Counter = Counter(1)
                counter.value = 2
                val present: MaybeInt = Just(counter.value)
                val absent: MaybeInt = Nothing
                var result: Int = 0
                when (present) {
                    Just -> { result = 1 }
                    Nothing -> { result = 0 }
                }
            }
            """.trimIndent(),
        )

        val result = BredToCCompilerFactory().create().compile(source)

        result.fold(
            ifLeft = { errors -> error("expected C code, got: $errors") },
            ifRight = { cLines ->
                assertTrue(
                    cLines.any { it == "typedef struct Counter { int value; } Counter;" },
                    "expected Counter struct definition: $cLines",
                )
                assertTrue(
                    cLines.any { it == "typedef struct MaybeInt { int tag; int Just_value0; } MaybeInt;" },
                    "expected MaybeInt tagged struct definition: $cLines",
                )
                assertTrue(
                    cLines.any { it.matches(Regex("""local_present_\d+ = \(MaybeInt\)\{\.tag = 1, \.Just_value0 = \w+\};""")) },
                    "expected Just designated initializer: $cLines",
                )
                assertTrue(
                    cLines.any { it.matches(Regex("""local_absent_\d+ = \(MaybeInt\)\{\.tag = 0\};""")) },
                    "expected Nothing designated initializer: $cLines",
                )
                assertTrue(
                    cLines.any { it.matches(Regex("""\w+ = local_present_\d+\.tag;""")) },
                    "expected when lowering to inspect the sum tag: $cLines",
                )
            },
        )
    }

    @Test
    fun `product field write and read use the declared C variable`(@TempDir dir: Path) {
        val source = dir.resolve("counter-field-access.bred")
        source.writeText(
            """
            type mut Counter(value: Int)

            fun main(): Unit {
                val counter: Counter = Counter(1)
                counter.value = 2
                val observed: Int = counter.value
            }
            """.trimIndent(),
        )

        val cLines = BredToCCompilerFactory().create().compile(source).fold(
            ifLeft = { errors -> error("expected C code, got: $errors") },
            ifRight = { it },
        ).map { it.trim() }
        fun singleName(pattern: Regex): String =
            cLines.mapNotNull { pattern.matchEntire(it)?.groupValues?.get(1) }.single()

        val declared = singleName(Regex("""Counter\s+(\w+);"""))
        val written = singleName(Regex("""(\w+)\.value\s*=.*;"""))
        val read = singleName(Regex("""\w+\s*=\s*(\w+)\.value;"""))
        assertEquals(listOf(declared, declared), listOf(written, read), "C receivers for field write and read")
    }

//    @Test
//    fun `deployment health radar example emits sum payload extraction c code`() {
//        val result = BredToCCompilerFactory().create().compile(Path.of("examples/3ac.bred"))
//
//        result.fold(
//            ifLeft = { errors -> error("expected C code, got: $errors") },
//            ifRight = { cLines ->
//                assertTrue(
//                    cLines.any { it == "typedef struct Health { int tag; int Degraded_value0; int Critical_score; } Health;" },
//                    "expected Health tagged struct definition: $cLines",
//                )
//                assertTrue(
//                    cLines.any { it == "typedef struct Alert { int tag; int Notify_level; String Notify_message; } Alert;" },
//                    "expected Alert tagged struct definition: $cLines",
//                )
//                assertTrue(
//                    cLines.any { it.contains("bestScore =") && it.contains(".Just_value;") },
//                    "expected Maybe Just payload extraction into bestScore: $cLines",
//                )
//                assertTrue(
//                    cLines.any { it.contains("level =") && it.contains(".Notify_level;") },
//                    "expected Alert level payload extraction: $cLines",
//                )
//                assertTrue(
//                    cLines.any { it.contains("message =") && it.contains(".Notify_message;") },
//                    "expected Alert message payload extraction: $cLines",
//                )
//                assertTrue(
//                    cLines.any { it.contains("score =") && it.contains(".Degraded_value0;") },
//                    "expected Degraded payload extraction: $cLines",
//                )
//                assertTrue(
//                    cLines.any { it.contains("score =") && it.contains(".Critical_score;") },
//                    "expected Critical payload extraction: $cLines",
//                )
//            },
//        )
//    }

    @Test
    fun `bred compiler returns parser errors on the left`(@TempDir dir: Path) {
        val source = dir.resolve("broken.bred")
        source.writeText("fun main(: Unit { }")

        val result = BredToCCompilerFactory().create().compile(source)

        result.fold(
            ifLeft = { errors ->
                assertTrue(errors.isNotEmpty())
                assertTrue(errors.any { it.contains("Parser error") }, "expected parser error, got: $errors")
            },
            ifRight = { cLines -> error("expected parser errors, got C code: $cLines") },
        )
    }

    @Test
    fun `bred compiler returns readable semantic errors on the left`(@TempDir dir: Path) {
        val source = dir.resolve("semantic-broken.bred")
        source.writeText(
            """
            fun main(): Unit {
                val n: Int = "wrong"
            }
            """.trimIndent(),
        )

        val result = BredToCCompilerFactory().create().compile(source)

        result.fold(
            ifLeft = { errors ->
                val message = errors.joinToString("\n")
                assertTrue(message.contains("Semantic error TYPE_CHECKER_INCOMPATIBLE_TYPES"), message)
                assertTrue(message.contains("initialize 'n: Int'"), message)
                assertTrue(message.contains("expected Int, got String"), message)
            },
            ifRight = { cLines -> error("expected semantic errors, got C code: $cLines") },
        )
    }
}

class CExecutableBuilderTest {
    @Test
    fun `builder prepends runtime writes c file builds exe and deletes intermediate by default`(@TempDir dir: Path) {
        val runtime = dir.resolve("runtime.c")
        val vcvars = dir.resolve("vcvars64.bat")
        val cFile = dir.resolve("out.c")
        val exeFile = dir.resolve("out.exe")
        runtime.writeText("/* runtime */")
        vcvars.writeText("@echo off")

        var cTextSeenByCompiler = ""
        val builder = CExecutableBuilder(
            runtimePath = runtime,
            vcvarsPath = vcvars.toString(),
            compilerInvoker = { _, cPath, exePath ->
                cTextSeenByCompiler = Files.readString(cPath)
                Files.writeString(exePath, "exe")
                CCompilerInvocationResult(0, listOf("compiled with /Od"))
            },
        )

        val messages = builder.build(
            cLines = listOf("int main(void) {", "return 0;", "}"),
            outputExe = exeFile,
            intermediateCFile = cFile,
        )

        assertTrue(Files.exists(exeFile), "expected executable to be created")
        assertTrue(Files.notExists(cFile), "expected intermediate C file to be deleted")
        assertTrue(cTextSeenByCompiler.startsWith("/* runtime */"), cTextSeenByCompiler)
        assertTrue(cTextSeenByCompiler.contains("int main(void) {"), cTextSeenByCompiler)
        assertTrue(messages.any { it.contains("Generated executable") }, "expected success message, got: $messages")
    }

    @Test
    fun `builder keeps intermediate c file when requested`(@TempDir dir: Path) {
        val runtime = dir.resolve("runtime.c")
        val vcvars = dir.resolve("vcvars64.bat")
        val cFile = dir.resolve("kept.c")
        val exeFile = dir.resolve("kept.exe")
        runtime.writeText("/* runtime */")
        vcvars.writeText("@echo off")

        val builder = CExecutableBuilder(
            runtimePath = runtime,
            vcvarsPath = vcvars.toString(),
            compilerInvoker = { _, _, exePath ->
                Files.writeString(exePath, "exe")
                CCompilerInvocationResult(0, emptyList())
            },
        )

        builder.build(
            cLines = listOf("int main(void) {", "return 0;", "}"),
            outputExe = exeFile,
            keepIntermediateC = true,
            intermediateCFile = cFile,
        )

        assertTrue(Files.exists(cFile), "expected intermediate C file to stay")
        assertEquals(
            listOf("/* runtime */", "int main(void) {", "return 0;", "}"),
            Files.readAllLines(cFile),
        )
    }

    @Test
    fun `builder can use runtime path from standard library provider`(@TempDir dir: Path) {
        val runtime = dir.resolve("custom-runtime.c")
        val vcvars = dir.resolve("vcvars64.bat")
        val cFile = dir.resolve("provider-runtime.c")
        val exeFile = dir.resolve("provider-runtime.exe")
        val provider = StandardLibraryProvider(runtimePath = runtime)
        runtime.writeText("/* provider runtime */")
        vcvars.writeText("@echo off")

        val builder = CExecutableBuilder(
            runtimePath = provider.runtimePath,
            vcvarsPath = vcvars.toString(),
            compilerInvoker = { _, _, exePath ->
                Files.writeString(exePath, "exe")
                CCompilerInvocationResult(0, emptyList())
            },
        )

        builder.build(
            cLines = listOf("int main(void) {", "return 0;", "}"),
            outputExe = exeFile,
            keepIntermediateC = true,
            intermediateCFile = cFile,
        )

        assertTrue(Files.readString(cFile).startsWith("/* provider runtime */"))
    }
}
