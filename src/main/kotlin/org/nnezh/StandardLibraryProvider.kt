package org.nnezh

import org.nnezh.bred.common.ExternalFunctionSpecs
import java.nio.file.Path

data class StandardLibraryProvider(
    val stdlibPath: Path = Path.of("stdlib.bred"),
    val runtimePath: Path = Path.of("runtime.c"),
    val externFunctions: List<ExternalFunctionSpecs.ExternalFunctionSpec> = ExternalFunctionSpecs.specs,
)
