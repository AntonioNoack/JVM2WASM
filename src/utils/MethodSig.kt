package utils

import me.anno.utils.assertions.assertTrue
import replaceClass1

@Suppress("DataClassPrivateConstructor")
data class MethodSig private constructor(
    val clazz: String,
    val name: String,
    val descriptor: String,
    val isStatic: Boolean
) {

    fun withClass(clazz: String): MethodSig {
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
            val clazz2 = if (clazz.startsWith("[[") || clazz.startsWith("[L")) "[]" else replaceClass1(clazz)
            if (clazz2.startsWith("[L")) throw IllegalArgumentException(clazz)
            if ('.' in clazz2) throw IllegalStateException(clazz)
            return clazz2
        }

        fun validateDescriptor(descriptor: String): String {
            val descriptor2 = if ('/' in descriptor) descriptor.replace('/', '_') else descriptor
            val descriptor3 = if ('$' in descriptor2) descriptor2.replace('$', 'X') else descriptor2
            val descriptor4 = if ('[' in descriptor3) descriptor3.replace('[', 'A') else descriptor3
            val j = descriptor4.indexOf(')')
            if (!descriptor4.startsWith('(') || j < 0 || j != descriptor4.lastIndexOf(')')) {
                throw IllegalArgumentException("Expected () in $descriptor4")
            }
            var i = 1
            search@ while (i < descriptor4.length) {
                when (descriptor4[i]) {
                    in "IJFDZSBC" -> i++
                    'V' -> {
                        assertTrue(i == descriptor4.lastIndex)
                        i++
                    }
                    'A' -> {
                        i++
                        assertTrue(descriptor4[i] !in ")V")
                    }
                    'L' -> {
                        // good :)
                        // skip until ;
                        // expect only latin chars and under-scores
                        while (true) {
                            when (descriptor4[i++]) {
                                in 'A'..'Z', in 'a'..'z', in '0'..'9', '_' -> {}
                                ';' -> continue@search
                                else -> throw IllegalArgumentException("Unexpected symbol in descriptor: $descriptor4")
                            }
                        }
                    }
                    ')' -> i++
                    else -> throw IllegalArgumentException("Unexpected symbol in descriptor: $descriptor4")
                }
            }
            return descriptor4
        }
    }
}