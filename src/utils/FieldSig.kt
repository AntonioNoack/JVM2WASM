package utils

import me.anno.utils.assertions.assertTrue

data class FieldSig(val clazz: String, val name: String, val descriptor: String, val static: Boolean) {
    init {
        assertTrue(!descriptor.endsWith(';'), descriptor)
    }

    // descriptor not necessarily needed here
    override fun toString() = (if (static) "s " else "") + clazz.replace('/', '.') + '.' + name + ": " + descriptor
}