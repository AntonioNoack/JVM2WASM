package utils

import replaceClass

@Suppress("DataClassPrivateConstructor")
data class MethodSig private constructor(
    val clazz: String,
    val name: String,
    val descriptor: Descriptor,
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
            val descriptor2 = Descriptor.c(descriptor)
            return MethodSig(clazz2, name, descriptor2, isStatic)
        }

        fun c(clazz: String, name: String, descriptor: Descriptor, isStatic: Boolean): MethodSig {
            val clazz2 = validateClassName(clazz)
            return MethodSig(clazz2, name, descriptor, isStatic)
        }

        private fun validateClassName(clazz: String): String {
            val clazz2 =
                if (clazz.startsWith("[[") || clazz.startsWith("[L")) "[]"
                else replaceClass(clazz)
            if (clazz2.startsWith("[L")) throw IllegalArgumentException(clazz)
            if ('.' in clazz2) throw IllegalStateException(clazz)
            return clazz2
        }
    }
}