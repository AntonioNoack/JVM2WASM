package translator

import wasm.instr.LocalGet
import wasm.instr.LocalSet
import wasm.instr.ParamGet
import wasm.instr.ParamSet

class LocalVar(
    val descriptor: String,
    val wasmType: String,
    val wasmName: String,
    val index: Int,
    isParam: Boolean
) {
    val localGet = if (isParam) ParamGet[index] else LocalGet(wasmName)
    val localSet = if (isParam) ParamSet[index] else LocalSet(wasmName)
}