package wasm.parser

import me.anno.utils.assertions.assertFalse

class GlobalVariable(
    val name: String, val type: String, var initialValue: Int,
    val isMutable: Boolean
) {

    val fullName = "global_$name"

    init {
        assertFalse(name.startsWith("global_"))
    }
}