package wasm.instr

import interpreter.WASMEngine

class NumberCastInstruction(
    name: String, val prefix: String, val suffix: String,
    val popType: String,val pushType: String,
    val impl: (Number) -> Number
) : SimpleInstr(name) {

    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        stack[stack.lastIndex] = impl(stack[stack.lastIndex])
        return null
    }
}