package utils

object NativeTypes {

    val nativeMapping = mapOf(
        'I' to "int",
        'J' to "long",
        'F' to "float",
        'D' to "double",
        'Z' to "boolean",
        'B' to "byte",
        'S' to "short",
        'C' to "char",
        'V' to "void"
    )

    val joined = nativeMapping.keys.joinToString("")
    val nativeMappingInv = nativeMapping.entries.associate { it.value to it.key }
    val nativeTypes = nativeMapping.values.toList()
    val nativeArrays = nativeMapping.keys.map { symbol -> "[$symbol" }

    val nativeTypeWrappers = mapOf(
        "int" to "java/lang/Integer",
        "long" to "java/lang/Long",
        "float" to "java/lang/Float",
        "double" to "java/lang/Double",
        "boolean" to "java/lang/Boolean",
        "byte" to "java/lang/Byte",
        "short" to "java/lang/Short",
        "char" to "java/lang/Character",
        "void" to "java/lang/Void"
    )

    fun isObjectArray(clazz: String): Boolean {
        return clazz.startsWith("[") &&
                clazz !in nativeArrays
    }
}