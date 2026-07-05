package org.nnezh.bred.common

object BuiltInMethods {
    val specs: List<ExternalFunctionSpecs.ExternalFunctionSpec> = ExternalFunctionSpecs.specs

    fun signatures(resolve: (TypeRef) -> TypeSign): List<FunctionSignature> =
        ExternalFunctionSpecs.signatures(specs, resolve)
}
