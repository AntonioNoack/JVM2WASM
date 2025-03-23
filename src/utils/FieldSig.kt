package utils

import hIndex
import me.anno.utils.assertions.assertTrue

class FieldSig(val clazz: String, val name: String, val descriptor: String, val isStatic: Boolean) {

    init {
        assertTrue(!descriptor.endsWith(';'), descriptor)
    }

    val isFinal: Boolean get() = this in hIndex.finalFields

    override fun equals(other: Any?): Boolean {
        return other is FieldSig &&
                other.clazz == clazz &&
                other.name == name
    }

    override fun hashCode(): Int {
        return clazz.hashCode() + name.hashCode() * 31
    }

    // descriptor not necessarily needed here
    override fun toString() = (if (isStatic) "s " else "") + clazz.replace('/', '.') + '.' + name + ": " + descriptor
}