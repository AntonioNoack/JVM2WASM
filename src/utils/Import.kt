package utils

import canThrowError
import hIndex
import useWASMExceptions

fun StringBuilder2.import1(name: String, inputs: List<String>, outputs: List<String>) {
    if (name == "kotlin_reflect_full_KClasses_getSuperclasses_Lkotlin_reflect_KClassLjava_util_List") TODO()
    if (contains("kotlin_reflect_full_KClasses_getSuperclasses_Lkotlin_reflect_KClassLjava_util_List")) TODO()
    append("(import \"jvm\" \"").append(name).append("\" (func $").append(name)
    if (inputs.isNotEmpty()) {
        append(" (param")
        for (input in inputs) {
            append(' ')
            append(input)
        }
        append(')')
    }
    append(" (result")
    for (output in outputs) {
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
        val name = methodName(sig)
        if (name !in hIndex.methodAliases ||
            (hIndex.annotations[sig] ?: emptyList()).any { it.clazz == "annotations/Alias" }
        ) {
            import1(
                name,
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