package utils

import replaceClass

@Suppress("DataClassPrivateConstructor")
data class MethodSig private constructor(
    val clazz: String, val name: String,
    val descriptor: Descriptor
) {

    fun withClass(clazz: String): MethodSig {
        if (clazz == this.clazz) return this
        return MethodSig(validateClassName(clazz), name, descriptor)
    }

    override fun toString() = "$clazz/$name$descriptor"

    // saving the hash brings a ~5% speedup, but it's very random :/
    private val hash = (clazz.hashCode() * 31 + name.hashCode()) * 31 + descriptor.hashCode()
    override fun hashCode(): Int = hash

    override fun equals(other: Any?): Boolean {
        return other === this || other is MethodSig &&
                other.hash == hash &&
                other.clazz == clazz &&
                other.name == name &&
                other.descriptor == descriptor
    }

    companion object {

        fun c(clazz: String, name: String, descriptor: String): MethodSig {
            val clazz2 = validateClassName(clazz)
            val descriptor2 = Descriptor.c(descriptor)
            return MethodSig(clazz2, name, descriptor2)
        }

        fun c(clazz: String, name: String, descriptor: Descriptor): MethodSig {
            val clazz2 = validateClassName(clazz)
            return MethodSig(clazz2, name, descriptor)
        }

        fun staticInit(clazz: String): MethodSig {
            return c(clazz, STATIC_INIT, "()V")
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