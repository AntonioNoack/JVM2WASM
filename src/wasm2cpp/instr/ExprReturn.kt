package wasm2cpp.instr

import wasm2cpp.StackElement

class ExprReturn(val results: List<StackElement>) : CppInstruction {
    override fun isReturning(): Boolean = true
}