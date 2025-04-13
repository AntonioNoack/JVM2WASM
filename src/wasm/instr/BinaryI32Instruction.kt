package wasm.instr

import interpreter.WASMEngine
import utils.WASMTypes.i32
import wasm.writer.Opcode

class BinaryI32Instruction(name: String, operator: BinaryOperator, opcode: Opcode, val impl: (Int, Int) -> Int) :
    BinaryInstruction(name, i32, i32, operator, opcode) {
    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val i1 = stack.removeLast() as Int
        val i0 = stack.removeLast() as Int
        stack.add(impl(i0, i1))
        return null
    }
}