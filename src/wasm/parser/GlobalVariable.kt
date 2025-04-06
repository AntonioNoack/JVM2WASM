package wasm.parser

import me.anno.utils.assertions.assertFalse
import utils.WASMType

class GlobalVariable(
    val name: String, val wasmType: WASMType, var initialValue: Int,
    val isMutable: Boolean
) {

    val fullName = "global_$name"
    var index = -1

    init {
        assertFalse(name.startsWith("global_"))
    }
}