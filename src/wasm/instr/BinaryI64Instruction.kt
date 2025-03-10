package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop
import utils.WASMTypes.i64

class BinaryI64Instruction(name: String, cppOperator: String, val impl: (Long, Long) -> Long) :
    BinaryInstruction(name, i64, cppOperator) {
    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val i1 = stack.pop() as Long
        val i0 = stack.pop() as Long
        stack.add(impl(i0, i1))
        return null
    }
}