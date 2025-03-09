package translator

import wasm.instr.LocalGet
import wasm.instr.LocalSet
import wasm.instr.ParamGet
import wasm.instr.ParamSet

class LocalVariableOrParam(
    val descriptor: String,
    val wasmType: String,
    var name: String,
    val index: Int,
    isParam: Boolean
) {
    val localGet = if (isParam) ParamGet(index, name) else LocalGet(name)
    val localSet = if (isParam) ParamSet(index, name) else LocalSet(name)

    val getter get() = localGet
    val setter get() = localSet

    val isParam get() = localGet is ParamGet

    fun renameTo(newName: String) {
        name = newName
        if (isParam) {
            (getter as ParamGet).name = newName
            (setter as ParamSet).name = newName
        } else {
            (getter as LocalGet).name = newName
            (setter as LocalSet).name = newName
        }
    }
}