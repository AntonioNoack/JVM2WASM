package wasm.instr

import interpreter.WASMEngine
import wasm.writer.Opcode

class NumberCastInstruction(
    name: String, val prefix: String, val suffix: String,
    popType: String, pushType: String, opcode: Opcode,
    val impl: (Number) -> Number
) : UnaryInstruction(name, popType, pushType, UnaryOperator.ANY_CAST, opcode) {

    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        stack[stack.lastIndex] = impl(stack[stack.lastIndex])
        return null
    }
}