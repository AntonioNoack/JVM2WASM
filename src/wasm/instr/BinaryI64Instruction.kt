package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop
import utils.WASMTypes.i64
import wasm.writer.Opcode

class BinaryI64Instruction(name: String, operator: BinaryOperator, opcode: Opcode, val impl: (Long, Long) -> Long) :
    BinaryInstruction(name, i64, i64, operator, opcode) {
    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val i1 = stack.pop() as Long
        val i0 = stack.pop() as Long
        stack.add(impl(i0, i1))
        return null
    }
}