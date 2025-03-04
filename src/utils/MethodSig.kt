package utils

import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertTrue
import replaceClass

@Suppress("DataClassPrivateConstructor")
data class MethodSig private constructor(
    val clazz: String,
    val name: String,
    val descriptor: String,
    val isStatic: Boolean
) {

    fun withClass(clazz: String): MethodSig {
        if (clazz == this.clazz) return this
        return MethodSig(validateClassName(clazz), name, descriptor, isStatic)
    }

    override fun toString() = "$clazz/$name$descriptor"

    override fun hashCode(): Int {
        // hash without static until we fix it
        var hash = 31 * clazz.hashCode()
        hash = hash * 31 + name.hashCode()
        hash = hash * 31 + descriptor.hashCode()
        return hash
    }

    override fun equals(other: Any?): Boolean {
        // equals without static until we fix it
        return other is MethodSig &&
                other.clazz == clazz &&
                other.name == name &&
                other.descriptor == descriptor
    }

    companion object {

        fun c(clazz: String, name: String, descriptor: String, isStatic: Boolean): MethodSig {
            val clazz2 = validateClassName(clazz)
            val descriptor4 = validateDescriptor(descriptor)
            return MethodSig(clazz2, name, descriptor4, isStatic)
        }

        private fun validateClassName(clazz: String): String {
            val clazz2 =
                if (clazz.startsWith("[[") || clazz.startsWith("[L")) "[]"
                else replaceClass(clazz)
            if (clazz2.startsWith("[L")) throw IllegalArgumentException(clazz)
            if ('.' in clazz2) throw IllegalStateException(clazz)
            return clazz2
        }

        fun validateDescriptor(descriptor: String): String {
            val j = descriptor.indexOf(')')
            if (!descriptor.startsWith('(') || j < 0 || j != descriptor.lastIndexOf(')')) {
                throw IllegalArgumentException("Expected () in $descriptor")
            }
            var i = 1
            val builder = StringBuilder2(descriptor.length)
            builder.append('(')
            search@ while (i < descriptor.length) {
                when (val char0 = descriptor[i]) {
                    in "IJFDZSBC" -> {
                        builder.append(char0)
                        i++
                    }
                    'V' -> {
                        builder.append('V')
                        assertTrue(i == descriptor.lastIndex) // must be last character
                        i++
                    }
                    '[', 'A' -> {
                        builder.append('A')
                        assertTrue(descriptor[i + 1] !in ")V") // next one must be neither ')' nor 'V'
                        i++
                    }
                    'L' -> {
                        // good :)
                        // skip until ;
                        // expect only latin chars and under-scores
                        val startI = i + 1
                        while (true) {
                            when (descriptor[i++]) {
                                in 'A'..'Z', in 'a'..'z', in '0'..'9', '_', '/', '$' -> {
                                    // ok
                                }
                                ';' -> {
                                    val endI = i - 1
                                    val className = descriptor.substring(startI, endI)
                                    builder.append('L')
                                    val b0 = builder.size
                                    builder.append(replaceClass(className))
                                    for (k in b0 until builder.length) {
                                        // replace chars as needed
                                        when (builder[k].toInt().toChar()) {
                                            '/' -> builder[k] = '_'.code.toByte()
                                            '$' -> builder[k] = '$'.code.toByte()
                                        }
                                    }
                                    builder.append(';')
                                    continue@search
                                }
                                else -> throw IllegalArgumentException("Unexpected symbol in descriptor: $descriptor")
                            }
                        }
                    }
                    ')' -> {
                        assertEquals(i, j) // must be at j-th place
                        builder.append(')')
                        i++
                    }
                    else -> throw IllegalArgumentException("Unexpected symbol in descriptor: $descriptor")
                }
            }
            // println("$descriptor -> $builder")
            return builder.toString()
        }
    }
}