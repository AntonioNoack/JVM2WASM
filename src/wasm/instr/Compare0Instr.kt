package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop
import wasm.writer.Opcode

class Compare0Instr(name: String, operator: BinaryOperator, type: String, opcode: Opcode) :
    CompareInstr(name, operator, type, null, opcode) {

    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val i1 = stack.pop()!!
        @Suppress("UNCHECKED_CAST")
        val i0 = stack.pop()!! as Comparable<Number>
        stack.add(if (impl(i0.compareTo(i1))) 1 else 0)
        return null
    }
}