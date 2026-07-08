package org.nnezh.lltag

import org.nnezh.org.nnezh.ICGenerator.PrettyPrinter
import org.nnezh.org.nnezh.compiler.TACCompilerImpl
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

fun main(args: Array<String>) {
    val programs = args.toList().ifEmpty { LLTAGSnapshotTester.SNAPSHOT_PROGRAMS }
    val resourcesDir = Paths.get("tac/src/test/resources")

    programs.forEach { program ->
        writeSnapshot(resourcesDir, program)
    }
}

private fun writeSnapshot(resourcesDir: Path, program: String) {
    val sourcePath = resourcesDir.resolve("$program.bred")
    val snapshotPath = resourcesDir.resolve("$program.3ac")
    val source = Files.readString(sourcePath)
    val tac = TACCompilerImpl().compile(source)
        .let { PrettyPrinter().format(it) }

    Files.write(snapshotPath, tac)
    println("wrote $snapshotPath")
}
