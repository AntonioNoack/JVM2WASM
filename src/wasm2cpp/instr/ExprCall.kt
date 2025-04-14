package wasm2cpp.instr

import wasm2cpp.StackElement

open class ExprCall(
    val funcName: String,
    val params: List<StackElement>,
) : CppInstruction