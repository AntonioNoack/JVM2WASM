package utils

import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertTrue
import replaceClass
import utils.NativeTypes.nativeMapping

class Descriptor private constructor(val params: List<String>, val returnType: String?, val raw: String) {

    val wasmParams = params.map(::jvm2wasmTyped)
    val wasmReturnType = if (returnType != null) jvm2wasmTyped(returnType) else null

    override fun equals(other: Any?): Boolean {
        return other is Descriptor && other.raw == raw
    }

    override fun hashCode(): Int = raw.hashCode()
    override fun toString(): String = raw

    fun getResultWASMTypes(canThrowError: Boolean): List<String> {
        // could be cached...
        val returnType = wasmReturnType
        return if (canThrowError) {
            if (returnType != null) listOf(returnType, ptrType)
            else listOf(ptrType)
        } else {
            if (returnType != null) listOf(returnType)
            else emptyList()
        }
    }

    companion object {

        // 26k entries... how is there sooo many values??? maybe we don't need to cache it...
        // we call this a million times, so each entry 40 times -> definitely worth it :)
        private val descriptorCache = HashMap<String, Descriptor>(1 shl 15)

        fun validateDescriptor(descriptor: String): Descriptor {
            return descriptorCache.getOrPut(descriptor) {
                parseDescriptor(descriptor)
            }
        }

        private fun parseDescriptor(descriptor: String): Descriptor {

            assertTrue("me_anno_utils" !in descriptor)

            val j = descriptor.indexOf(')')
            if (!descriptor.startsWith('(') || j < 0 || j != descriptor.lastIndexOf(')')) {
                throw IllegalArgumentException("Expected () in $descriptor")
            }
            var i = 1
            var i0 = 1
            val args = ArrayList<String>()
            val builder = StringBuilder2(descriptor.length)
            builder.append('(')
            search@ while (i < descriptor.length) {
                when (val char0 = descriptor[i++]) {
                    in "IJFDZSBC" -> {
                        builder.append(char0)
                        args.add(
                            if (i - i0 == 1) nativeMapping[char0]!!
                            else descriptor.substring(i0, i)
                        )
                        i0 = i
                    }
                    'V' -> {
                        builder.append('V')
                        assertTrue(i == descriptor.length) // must be last character
                    }
                    '[', 'A' -> {
                        builder.append('A')
                        assertTrue(descriptor[i] !in ")V") // next one must be neither ')' nor 'V'
                    }
                    'L' -> {
                        // good :)
                        // skip until ;
                        // expect only latin chars and under-scores
                        val startI = i
                        while (true) {
                            when (descriptor[i++]) {
                                in 'A'..'Z', in 'a'..'z', in '0'..'9', '_', '/', '$' -> {
                                    // ok
                                }
                                ';' -> {
                                    val endI = i - 1
                                    val depth = startI - i0 - 1
                                    val className = descriptor.substring(startI, endI)
                                    for (k in 0 until depth) builder.append('[')
                                    builder.append('L')
                                    val b0 = builder.size
                                    val className2 = replaceClass(className)
                                    builder.append(className2)
                                    if (depth > 0) {
                                        // to do optimize allocation, we can reuse the outer builder
                                        args.add("[".repeat(depth) + className2)
                                    } else {
                                        args.add(className2)
                                    }
                                    for (k in b0 until builder.length) {
                                        // replace chars as needed
                                        when (builder[k].toInt().toChar()) {
                                            '/' -> builder[k] = '_'.code.toByte()
                                            '$' -> builder[k] = '$'.code.toByte()
                                        }
                                    }
                                    builder.append(';')
                                    i0 = i
                                    continue@search
                                }
                                else -> throw IllegalArgumentException("Unexpected symbol in descriptor: $descriptor")
                            }
                        }
                    }
                    ')' -> {
                        assertEquals(i - 1, i0)
                        assertEquals(i - 1, j) // must be at j-th place
                        builder.append(')')
                        i0++ // skip ')'
                    }
                    else -> throw IllegalArgumentException("Unexpected symbol in descriptor: $descriptor")
                }
            }

            val returnType = if (!descriptor.endsWith(")V")) args.removeLast() else null
            val descriptor1 = Descriptor(args, returnType, builder.toString())
            println("'$descriptor' -> '$builder', $args, $returnType")
            return descriptor1
        }
    }
}