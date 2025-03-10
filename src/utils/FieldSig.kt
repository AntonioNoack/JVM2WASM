package utils

import hIndex
import me.anno.utils.assertions.assertTrue

data class FieldSig(val clazz: String, val name: String, val descriptor: String, val isStatic: Boolean) {
    init {
        assertTrue(!descriptor.endsWith(';'), descriptor)
    }

    // descriptor not necessarily needed here
    override fun toString() = (if (isStatic) "s " else "") + clazz.replace('/', '.') + '.' + name + ": " + descriptor

    val isFinal: Boolean get() = this in hIndex.finalFields
}