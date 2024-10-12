package wasm.parser

import wasm.instr.Instruction

data class FunctionImpl(
    val funcName: String, val params: List<String>, val results: List<String>,
    val locals: List<LocalVariable>,
    val body: List<Instruction>,
    val isExported: Boolean
)