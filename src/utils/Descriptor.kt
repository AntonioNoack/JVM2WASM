package utils

import hIndex
import me.anno.utils.assertions.assertFail
import me.anno.utils.assertions.assertTrue
import me.anno.utils.types.Booleans.toInt
import me.anno.utils.types.Strings.indexOf2
import replaceClass1
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
            if (generics) n else replaceClass1(n)
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

fun split3(d: String, generics: Boolean = false): List<String> {
    val ix = d.lastIndexOf(')')
    return split1(d.substring(1, ix), generics) as ArrayList
}

fun genericsTypes(sig: MethodSig): String {
    return genericsTypes(sig.descriptor, sig in hIndex.staticMethods)
}

fun genericsTypes(d: String, static: Boolean = true): String {
    val result = StringBuilder2(d.length + 1)
    if (!static) result.append('?')
    var i = 0
    while (i < d.length) {
        val k = i
        if (d[i] == '(') {
            i++
            continue
        }
        if (d[i] == ')') {
            i++
            result.append(')')
            continue
        }
        while (d[i] in "[A") i++
        result.append(
            when (val c = d[i++]) {
                'Z', 'C', 'B', 'S', 'I', 'J', 'F', 'D' -> if (d[k] !in "[A") c else '?'
                'L', 'T' -> {
                    // read until ;
                    i = d.indexOf(';', i + 1) + 1
                    '?'
                }
                'V' -> 'V'
                else -> throw IllegalArgumentException(d)
            }
        )
    }
    return result.toString()
}

fun splitToType(static: Boolean, descriptor: String, canThrow: Boolean): FuncType {
    assertTrue(descriptor.startsWith('('))
    var i = 1 // skip '('

    fun readType(): String {
        return when (descriptor[i++]) {
            'Z', 'C', 'B', 'S', 'I' -> i32
            'J' -> i64
            'F' -> f32
            'D' -> f64
            'L' -> {
                // read until ;
                i = descriptor.indexOf(';', i + 1) + 1
                ptrType
            }
            '[', 'A' -> {
                readType()
                ptrType
            }
            else -> assertFail(descriptor)
        }
    }

    val closeIdx = descriptor.indexOf(')')
    assertTrue(closeIdx > 0, descriptor)

    val params = ArrayList<String>()
    if (!static) params.add(ptrType)
    while (i < closeIdx) {
        params.add(readType())
    }

    val results = if (descriptor[closeIdx + 1] != 'V') {
        i = closeIdx + 1
        val type = readType()
        if (canThrow) listOf(type, ptrType)
        else listOf(type)
    } else {
        if (canThrow) listOf(ptrType)
        else emptyList()
    }

    return FuncType(params, results)
}

fun jvm2wasm(d: String): String = when (d) {
    "Z", "C", "S", "B", "I" -> i32
    "F" -> f32
    "D" -> f64
    "J" -> i64
    "V" -> ""
    else -> ptrType
}

fun jvm2wasm1(d: Char): String = when (d) {
    'Z', 'C', 'B', 'S', 'I' -> i32
    'J' -> i64
    'F' -> f32
    'D' -> f64
    else -> ptrType
}

fun jvm2wasm1(d: String): String = jvm2wasm1(d[0])

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
    val alias = hIndex.annotations[sig]
        ?.firstOrNull { it.clazz == "annotations/Alias" }
        ?: return emptyList()
    @Suppress("UNCHECKED_CAST")
    return alias.properties["names"] as List<String>
}

fun methodName(sig: MethodSig): String {
    val aliases = getAliases(sig)
    return if (aliases.isNotEmpty()) aliases.first()
    else methodName2(sig.clazz, sig.name, sig.descriptor)
}

fun methodNames(sig: MethodSig): List<String> {
    return getAliases(sig).ifEmpty {
        listOf(methodName2(sig.clazz, sig.name, sig.descriptor))
    }
}

fun methodName(clazz: String, name: String, descriptor: String, isStatic: Boolean): String {
    return methodName(MethodSig.c(clazz, name, descriptor, isStatic))
}

fun methodName(clazz: String, sig: InterfaceSig): String {
    return methodName(MethodSig.c(clazz, sig.name, sig.descriptor, false))
}

private val methodName2Cache = HashMap<Triple<String, String, String>, String>(1024)

fun methodName2(clazz: String, name: String, args: String): String {
    return methodName2Cache.getOrPut(Triple(clazz, name, args)) {
        when (name) {
            "<clinit>" -> "static|$clazz|$args"
            "<init>" -> "new|$clazz|$args"
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