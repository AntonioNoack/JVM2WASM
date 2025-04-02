package wasm2cpp.instr

import wasm2cpp.StackElement

class ExprCall(
    val funcName: String,
    val params: List<StackElement>,
    val resultTypes: List<String>,
    val resultName: String?
) : CppInstruction