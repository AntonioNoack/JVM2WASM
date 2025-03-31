package wasm.parser

import me.anno.utils.assertions.assertFalse

class GlobalVariable(
    val name: String, val wasmType: String, var initialValue: Int,
    val isMutable: Boolean
) {

    val fullName = "global_$name"
    var index = -1

    init {
        assertFalse(name.startsWith("global_"))
    }
}