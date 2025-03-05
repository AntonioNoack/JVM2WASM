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
        'C' to "char"
    )
    val nativeMappingInv = nativeMapping.entries.associate { it.value to it.key }
    val nativeTypes = nativeMapping.values.toList()
}