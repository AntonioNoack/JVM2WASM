package utils

import canThrowError
import hIndex
import useWASMExceptions
import wasm.parser.Import

fun StringBuilder2.import1(funcName: String, params: List<String>, results: List<String>) {
    imports.add(Import(funcName, params, results))
    append("(import \"jvm\" \"").append(funcName).append("\" (func $").append(funcName)
    if (params.isNotEmpty()) {
        append(" (param")
        for (input in params) {
            append(' ')
            append(input)
        }
        append(')')
    }
    append(" (result")
    for (output in results) {
        append(' ')
        append(output)
    }
    append(")))\n")
}

fun StringBuilder2.import2(sig: MethodSig) {
    val desc = sig.descriptor
    if (hIndex.hasAnnotation(sig, Annotations.WASM)) return
    if (sig in hIndex.abstractMethods) return
    if (hIndex.getAlias(sig) == sig || hIndex.hasAnnotation(sig, Annotations.ALIAS)) {
        val self = if (sig in hIndex.staticMethods) emptyList() else listOf(ptrType)
        val params = desc.wasmParams
        val canThrowError1 = canThrowError(sig) && !useWASMExceptions
        val returned = desc.getResultWASMTypes(canThrowError1)
        import1(methodName(sig), self + params, returned)
    }
}