package utils

import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertNull
import me.anno.utils.assertions.assertTrue
import replaceClass
import utils.NativeTypes.nativeMapping

class Descriptor private constructor(val params: List<String>, val returnType: String?, val raw: String) {

    val wasmParams = params.map(::jvm2wasmTyped)
    val wasmReturnType = if (returnType != null) jvm2wasmTyped(returnType) else null

    override fun equals(other: Any?): Boolean {
        return other === this || other is Descriptor && other.raw == raw
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

        fun c(descriptor: String): Descriptor {
            return descriptorCache.getOrPut(descriptor) {
                parseDescriptor(descriptor, emptyMap())
            }
        }

        private val typeCache = HashMap<String, String>(1 shl 10)

        fun parseType(descriptor: String): String {
            return typeCache.getOrPut(descriptor) {
                parseTypeImpl(descriptor)
            }
        }

        fun parseTypeMixed(descriptor: String): String {
            return if (descriptor.endsWith(";")) {
                // todo this really shouldn't happen, why are we getting mixed results???
                // assertTrue(descriptor.startsWith(";")) // maybe arrays are the cause?? -> nope
                parseType(descriptor)
            } else replaceClass(descriptor)
        }

        private fun parseTypeImpl(descriptor: String): String {
            val tmp = "($descriptor)V" // would be nice if we could avoid that
            val desc = c(tmp)
            assertNull(desc.returnType)
            assertEquals(1, desc.params.size)
            return desc.params[0]
        }

        fun parseDescriptor(descriptor: String, generics: Map<String, String>): Descriptor {

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
                    'V' -> {
                        builder.append('V')
                        assertTrue(i == descriptor.length) // must be last character
                    }
                    in NativeTypes.joined -> {
                        builder.append(char0)
                        args.add(
                            if (i - i0 == 1) nativeMapping[char0]!!
                            else descriptor.substring(i0, i)
                        )
                        i0 = i
                    }
                    '[', 'A' -> {
                        builder.append('A')
                        assertTrue(descriptor[i] !in ")V") // next one must be neither ')' nor 'V'
                    }
                    'L', 'T' -> {
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
                                    builder.size -= depth // delete Array-markers for now
                                    assertTrue(depth >= 0)
                                    val className = descriptor.substring(startI, endI)

                                    val b0 = builder.size

                                    val className2 = if (char0 == 'L') {
                                        replaceClass(className)
                                    } else {
                                        generics[className]
                                            ?: throw IllegalStateException("Missing generics $className in $generics")
                                    }

                                    // println("  [$depth] $className -> $className2 | $builder")

                                    // decide different cases:
                                    //  - native array -> AX
                                    //  - any object array -> AW
                                    //  - not an array -> L...;

                                    val isNativeArray =
                                        depth == 0 && className2 in NativeTypes.nativeArrays
                                    val anyObjectArray =
                                        depth > 0 || NativeTypes.isObjectArray(className2)

                                    when {
                                        isNativeArray -> {
                                            builder.append(className2)
                                            builder[builder.length - 2] = 'A'.code.toByte()
                                        }
                                        anyObjectArray -> {
                                            builder.append("AW") // old replacement for []
                                        }
                                        else -> {
                                            builder.append('L')
                                            builder.append(className2)
                                            builder.append(';')

                                            for (k in b0 until builder.length) {
                                                // replace chars as needed
                                                when (builder[k].toInt().toChar()) {
                                                    '/' -> builder[k] = '_'.code.toByte()
                                                    '$' -> builder[k] = '$'.code.toByte()
                                                    '[' -> builder[k] = 'A'.code.toByte()
                                                }
                                            }
                                        }
                                    }

                                    // to do optimize allocation, we can reuse the outer builder
                                    args.add(
                                        if (depth > 0) "[".repeat(depth) + className2
                                        else className2
                                    )

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
            // println("'$descriptor' -> '$builder', $args, $returnType, $generics")
            assertTrue(";;" !in descriptor1.raw, descriptor1.raw)
            assertTrue("A[" !in descriptor1.raw, descriptor1.raw)
            return descriptor1
        }

        init {
            // run a few tests
            val objArrayTest = c("([[[Ljava/lang/Object;)V")
            assertEquals("(AW)V", objArrayTest.raw)
            assertEquals(listOf("[[[java/lang/Object"), objArrayTest.params)
            assertNull(objArrayTest.returnType)

            val nativeArrayTest = c("([[[Z)V")
            assertEquals("(AAAZ)V", nativeArrayTest.raw)
            assertEquals(listOf("[[[Z"), nativeArrayTest.params)
            assertNull(nativeArrayTest.returnType)

            val nativeTest = c("(IZB)D")
            assertEquals("(IZB)D", nativeTest.raw)
            assertEquals(listOf("int", "boolean", "byte"), nativeTest.params)
            assertEquals("double", nativeTest.returnType)

            // generic tests
            val genericNormal = parseDescriptor("(TX;)V", mapOf("X" to "java/lang/Object"))
            assertEquals("(Ljava_lang_Object;)V", genericNormal.raw)
            val genericArray = parseDescriptor("(TX;)V", mapOf("X" to "[I"))
            assertEquals("(AI)V", genericArray.raw)
            val genericArray2 = parseDescriptor("(TX;)V", mapOf("X" to "[[I"))
            assertEquals("(AW)V", genericArray2.raw) // could be (AAI)V, too
            val genericArray3 = parseDescriptor("([TX;)V", mapOf("X" to "[I"))
            assertEquals("(AW)V", genericArray3.raw) // must be (AW)V
        }
    }
}