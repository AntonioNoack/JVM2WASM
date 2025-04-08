package wasm.parser

import me.anno.utils.assertions.assertFalse
import me.anno.utils.assertions.assertTrue
import utils.WASMType

class GlobalVariable(
    val name: String, val wasmType: WASMType, var initialValue: Number,
    val isMutable: Boolean
) {

    val fullName = "global_$name"
    var index = -1

    init {
        assertFalse(name.startsWith("global_"))
        assertTrue(
            when (wasmType) {
                WASMType.I32 -> initialValue is Int
                WASMType.I64 -> initialValue is Long
                WASMType.F32 -> initialValue is Float
                WASMType.F64 -> initialValue is Double
            }
        )
    }
}