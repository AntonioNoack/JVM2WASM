package translator

import utils.WASMType

data class LocalVarInfo(
    val name: String?, val descriptor: String, val signature: String?,
    val start: Int, val end: Int, val index: Int, val wasmType: WASMType
) {
    override fun toString(): String {
        return "\"$name\",\"$descriptor\",$start-$end,#$index"
    }
}