package utils

object KotlynReflect {

    private const val COMMON_PREFIX = "kotlin/"
    private val paths = listOf(
        "reflect/",
        "jvm/internal/Property",
        "jvm/internal/Reflection",
        "jvm/internal/MutableProperty",
        "jvm/JvmClassMappingKt",
    )

    fun replaceClass(clazz: String): String {
        return if (needsReplacement(clazz)) {
            replaceClassName(clazz)
        } else clazz
    }

    private fun needsReplacement(clazz: String): Boolean {
        if (!clazz.startsWith(COMMON_PREFIX)) return false
        val startIndex = COMMON_PREFIX.length
        for (i in paths.indices) {
            if (clazz.startsWith(paths[i], startIndex)) {
                return true
            }
        }
        return false
    }

    private val builder = StringBuilder2()
    private fun replaceClassName(clazz: String): String {
        builder.clear()
        builder.append(clazz)
        builder[4] = 'y'.code.toByte()
        return builder.toString()
    }

}