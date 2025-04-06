package translator

import me.anno.utils.assertions.assertFalse
import utils.WASMType
import utils.WASMTypes.isWASMType
import wasm.instr.LocalGet
import wasm.instr.LocalSet
import wasm.instr.ParamGet
import wasm.instr.ParamSet

class LocalVariableOrParam(
    val jvmType: String,
    val wasmType: WASMType,
    var name: String,
    val index: Int,
    isParam: Boolean
) {

    init {
        assertFalse(isWASMType(jvmType), jvmType)
    }

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

    override fun toString(): String {
        return "$wasmType $name"
    }
}