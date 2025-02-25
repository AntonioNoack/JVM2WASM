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
    val di = desc.lastIndexOf(')')
    val wasm = hIndex.annotations[sig]?.filter { it.clazz == "annotations/WASM" }
    if (wasm.isNullOrEmpty()) {
        if (sig in hIndex.abstractMethods) return
        if (hIndex.getAlias(sig) == sig ||
            (hIndex.annotations[sig] ?: emptyList()).any { it.clazz == "annotations/Alias" }
        ) {
            import1(
                methodName(sig),
                (if (sig in hIndex.staticMethods) emptyList() else listOf(ptrType)) +
                        split1(desc.substring(1, di)).map { jvm2wasm(it) },
                if (canThrowError(sig) && !useWASMExceptions) {
                    if (desc.endsWith(")V")) {
                        listOf(ptrType)
                    } else {
                        listOf(jvm2wasm1(desc[di + 1]), ptrType)
                    }
                } else {
                    if (desc.endsWith(")V")) {
                        emptyList()
                    } else {
                        listOf(jvm2wasm1(desc[di + 1]))
                    }
                }
            )
        }
    }
}