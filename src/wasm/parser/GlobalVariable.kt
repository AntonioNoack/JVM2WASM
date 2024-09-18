package wasm.parser

class GlobalVariable(
    val name: String, val type: String, val initialValue: Int,
    val isMutable: Boolean
)