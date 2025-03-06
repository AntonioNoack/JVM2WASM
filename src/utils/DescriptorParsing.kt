package utils

import hIndex
import wasm.instr.FuncType

val f32 = "f32"
val f64 = "f64"
val i32 = "i32"
val i64 = "i64"

// could be changed to i64 in the future, if more browsers support 64 bit wasm
// quite a few bits expect i32 though... hard to change now
val is32Bits = true
val ptrType = if (is32Bits) i32 else i64

fun genericsTypes(sig: MethodSig): String {
    return genericsTypes(sig.descriptor, sig in hIndex.staticMethods)
}

fun genericsTypes(desc: Descriptor, isStatic: Boolean): String {
    val d = desc.raw
    val result = StringBuilder2(d.length + 1)
    if (!isStatic) result.append('?')

    for (param in desc.params) {
        val char = NativeTypes.nativeMappingInv[param] ?: '?'
        result.append(char)
    }

    if (desc.returnType != null) {
        val char = NativeTypes.nativeMappingInv[desc.returnType] ?: '?'
        result.append(char)
    } else {
        result.append('V')
    }

    return result.toString()
}

fun descriptorToFuncType(isStatic: Boolean, descriptor: Descriptor, canThrow: Boolean): FuncType {
    val params = if (isStatic) descriptor.params
    else run {
        // should be cacheable, too... idk if that's worth it
        val params = ArrayList<String>(descriptor.wasmParams.size + 1)
        params.add(ptrType) // self
        params.addAll(descriptor.wasmParams)
        params
    }
    val results = descriptor.getResultWASMTypes(canThrow)
    return FuncType(params, results)
}

fun jvm2wasmTyped(d: String): String = when (d) {
    "Z", "C", "S", "B", "I", "F", "D", "J", "V" ->
        throw IllegalArgumentException("Use jvm2wasmRaw")
    "boolean", "char", "short", "byte", "int" -> i32
    "float" -> f32
    "double" -> f64
    "long" -> i64
    else -> ptrType
}

fun storageSize(d: String): Int = when (d) {
    "boolean", "byte" -> 1
    "char", "short" -> 2
    "int", "float" -> 4
    "long", "double" -> 8
    else -> if (is32Bits) 4 else 8
}

fun String.escapeChars() =
    this.filter {
        when (it) {
            ';', '(', ')' -> false
            else -> true
        }
    }.map {
        when (it) {
            '|', '/' -> '_'
            '[' -> 'A'
            ']' -> 'W'
            '$' -> 'X'
            '?' -> 'Y'
            '-' -> 'v'
            else -> it
        }
    }.joinToString("")

private fun getAliases(sig: MethodSig): List<String> {
    val alias = hIndex.getAnnotation(sig, Annotations.ALIAS)
        ?: return emptyList()
    @Suppress("UNCHECKED_CAST")
    return alias.properties["names"] as List<String>
}

fun methodName(sig: MethodSig): String {
    val aliases = getAliases(sig)
    return if (aliases.isNotEmpty()) aliases.first()
    else methodName2(sig.clazz, sig.name, sig.descriptor.raw)
}

fun methodNames(sig: MethodSig): List<String> {
    return getAliases(sig).ifEmpty {
        listOf(methodName2(sig.clazz, sig.name, sig.descriptor.raw))
    }
}

fun methodName(clazz: String, name: String, descriptor: String, isStatic: Boolean): String {
    return methodName(MethodSig.c(clazz, name, descriptor, isStatic))
}

fun methodName(clazz: String, sig: InterfaceSig): String {
    return methodName(MethodSig.c(clazz, sig.name, sig.descriptor.raw, false))
}

private val methodName2Cache = HashMap<Triple<String, String, String>, String>(1024)

fun methodName2(clazz: String, name: String, args: String): String {
    return methodName2Cache.getOrPut(Triple(clazz, name, args)) {
        when (name) {
            STATIC_INIT -> "static|$clazz|$args"
            INSTANCE_INIT -> "new|$clazz|$args"
            else -> "$clazz|$name|$args"
        }.escapeChars()
    }
}

fun descWithoutGenerics(desc: String): String {
    val idx = desc.indexOf('<')
    if (idx < 0) return desc
    if ('.' !in desc) {
        val idx2 = desc.indexOf('<', idx + 1)
        if (idx2 < 0) return desc.substring(0, idx) + desc.substring(desc.lastIndexOf('>') + 1)
    }
    val builder = StringBuilder(desc.length - 3)
    builder.append(desc, 0, idx)
    val i = idx + 1
    var count = 1
    for (j in i until desc.length) {
        when (val c = desc[j]) {
            '<' -> count++
            '>' -> count--
            else -> if (count == 0) {
                builder.append(if (c == '.') '$' else c)
            }
        }
    }
    return builder.toString()
}