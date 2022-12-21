package utils

data class FieldSig(val clazz: String, val name: String, val descriptor: String, val static: Boolean) {
    // descriptor not necessarily needed here
    override fun toString() = (if (static) "s " else "") + clazz.replace('/', '.') + '.' + name + ": " + descriptor
}