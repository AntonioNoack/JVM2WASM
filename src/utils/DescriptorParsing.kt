package utils

import hIndex
import jvm.JVMFlags.is32Bits
import translator.JavaTypes.convertTypeToWASM
import utils.WASMTypes.i32
import utils.WASMTypes.i64
import wasm.instr.FuncType

// could be changed to i64 in the future, if more browsers support 64 bit wasm
// quite a few bits expect i32 though... hard to change now
val ptrType = if (is32Bits) i32 else i64
val ptrTypeI = if (is32Bits) WASMType.I32 else WASMType.I64

fun genericsTypes(sig: MethodSig): String {
    return genericsTypes(sig.descriptor, hIndex.isStatic(sig))
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
    val params = if (isStatic) descriptor.wasmParams
    else run {
        // should be cacheable, too... idk if that's worth it
        val params = ArrayList<WASMType>(descriptor.wasmParams.size + 1)
        params.add(ptrTypeI) // self
        params.addAll(descriptor.wasmParams)
        params
    }
    val results = descriptor.getResultTypes(canThrow)
    return FuncType(params, results.map { convertTypeToWASM(it) })
}

fun jvm2wasmTyped(d: String): WASMType = when (d) {
    "Z", "C", "S", "B", "I", "F", "D", "J", "V" ->
        throw IllegalArgumentException("Use jvm2wasmRaw")
    "boolean", "char", "short", "byte", "int" -> WASMType.I32
    "float" -> WASMType.F32
    "double" -> WASMType.F64
    "long" -> WASMType.I64
    else -> ptrTypeI
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
    else methodName2(sig)
}

fun methodNames(sig: MethodSig): List<String> {
    return getAliases(sig).ifEmpty {
        listOf(methodName2(sig))
    }
}

fun methodName(clazz: String, name: String, descriptor: String): String {
    return methodName(MethodSig.c(clazz, name, descriptor))
}

fun methodName(clazz: String, sig: InterfaceSig): String {
    return methodName(MethodSig.c(clazz, sig.name, sig.descriptor.raw))
}

private val methodName2Cache = HashMap<Triple<String, String, String>, String>(1 shl 14)

fun methodName2(sig: MethodSig): String {
    return methodName2(sig.className, sig.name, sig.descriptor.raw)
}

fun methodName2(clazz: String, name: String, args: String): String {
    val clazz2 = if (NativeTypes.isObjectArray(clazz)) "[]" else clazz
    return methodName2Cache.getOrPut(Triple(clazz2, name, args)) {
        when (name) {
            STATIC_INIT -> "static|$clazz2|()V"
            INSTANCE_INIT -> "new|$clazz2|$args"
            else -> "$clazz2|$name|$args"
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