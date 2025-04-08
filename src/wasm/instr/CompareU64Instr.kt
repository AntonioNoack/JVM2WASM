package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop
import wasm.writer.Opcode

class CompareU64Instr(name: String, operator: String, opcode: Opcode) :
    CompareInstr(name, operator, "i64", "u64", opcode) {

    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val i1 = (stack.pop() as Long).toULong()
        val i0 = (stack.pop() as Long).toULong()
        stack.add(if (impl(i0.compareTo(i1))) 1 else 0)
        return null
    }
}