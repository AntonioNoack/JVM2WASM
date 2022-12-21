package utils

import reb

data class MethodSig private constructor(val clazz: String, val name: String, val descriptor: String) {

    init {
        if (clazz.startsWith("[L")) throw IllegalArgumentException("$clazz/$name/$descriptor")
        if ('.' in clazz) throw IllegalStateException(clazz)
    }

    override fun toString() = clazz.replace('.', '/') + '/' + name + descriptor

    companion object {
        fun c(clazz: String, name: String, descriptor: String): MethodSig {
            val clazz2 = if (clazz.startsWith("[[") || clazz.startsWith("[L")) "[]" else reb(clazz)
            return MethodSig(clazz2, name, descriptor)
        }
    }
}