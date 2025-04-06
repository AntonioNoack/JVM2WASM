package wasm.parser

import utils.WASMType

data class LocalVariable(val name: String, val jvmType: String, val wasmType: WASMType)