package wasm.parser

import me.anno.utils.assertions.assertTrue

class GlobalVariable(
    val name: String, val type: String, val initialValue: Int,
    val isMutable: Boolean
) {
    init {
        assertTrue(name.startsWith("global_"))
    }
}