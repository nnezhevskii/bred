import org.nnezh.org.nnezh.compiler.TACCompilerImpl

fun tryCompile(name: String, src: String) {
    try {
        val r = TACCompilerImpl().compile(src)
        println("$name: OK (${r.lines().size} lines)")
    } catch (e: Exception) {
        println("$name: ${e.message}")
    }
}

tryCompile("param to", "fun f(to: Int): Int { return to }\nfun main(): Unit { }")
tryCompile("var to", "fun main(): Unit { var to: Int = 5 }")
tryCompile("param end", "fun f(end: Int): Int { return end }\nfun main(): Unit { }")
