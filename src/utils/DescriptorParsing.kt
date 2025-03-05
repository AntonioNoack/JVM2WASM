package utils

import hIndex
import me.anno.utils.assertions.assertFail
import me.anno.utils.types.Booleans.toInt
import me.anno.utils.types.Strings.indexOf2
import replaceClass
import wasm.instr.FuncType

val f32 = "f32"
val f64 = "f64"
val i32 = "i32"
val i64 = "i64"

// could be changed to i64 in the future, if more browsers support 64 bit wasm
// quite a few bits expect i32 though... hard to change now
val is32Bits = true
val ptrType = if (is32Bits) i32 else i64

fun single(d: String, generics: Boolean = false): String {
    var i = 0
    while (d[i] in "[A") i++
    val pad = generics.toInt()
    return d.substring(0, i) + when (d[i++]) {
        'Z' -> "Z"
        'C' -> "C"
        'B' -> "B"
        'S' -> "S"
        'I' -> "I"
        'J' -> "J"
        'F' -> "F"
        'D' -> "D"
        'V' -> "V"
        'L', 'T' -> {
            // read until ;
            val j = d.indexOf2(';', i + 1)
            val n = d.substring(i - pad, j + pad)
            if (generics) n else replaceClass(n)
        }

        else -> throw IllegalArgumentException(d)
    }
}

fun split1(d: String, generics: Boolean = false): List<String> {
    val result = ArrayList<String>()
    var i = 0
    val pad = generics.toInt()
    fun readType(): String {
        return when (d[i++]) {
            'Z' -> "Z"
            'C' -> "C"
            'B' -> "B"
            'S' -> "S"
            'I' -> "I"
            'J' -> "J"
            'F' -> "F"
            'D' -> "D"
            'L', 'T' -> {
                // read until ;
                val j = d.indexOf(';', i + 1)
                val str = d.substring(i - pad, j + pad)
                i = j + 1
                str
            }
            '[', 'A' -> "[${readType()}"
            else -> assertFail(d)
        }
    }
    while (i < d.length) {
        result.add(readType())
    }
    return result
}

fun split2(d: String, generics: Boolean = false): List<String> {
    val ix = d.lastIndexOf(')')
    val base = split1(d.substring(1, ix), generics) as ArrayList
    base.add(single(d.substring(ix + 1), generics))
    return base
}

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

fun jvm2wasmRaw(d: String): String = when (d) {
    "boolean", "char", "short", "byte", "int", "float", "double", "long" ->
        throw IllegalArgumentException("Use jvm2wasmTyped")
    "Z", "C", "S", "B", "I" -> i32
    "F" -> f32
    "D" -> f64
    "J" -> i64
    "V" -> ""
    else -> ptrType
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

fun jvm2wasm1(d: Char): String = when (d) {
    'Z', 'C', 'B', 'S', 'I' -> i32
    'J' -> i64
    'F' -> f32
    'D' -> f64
    else -> ptrType
}

fun storageSize(d: String): Int = when (d[0]) {
    'Z', 'B' -> 1
    'C', 'S' -> 2
    'I', 'F' -> 4
    'J', 'D' -> 8
    'L', '[', 'A' -> if (is32Bits) 4 else 8
    else -> throw IllegalArgumentException(d)
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