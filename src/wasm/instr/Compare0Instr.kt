package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop

class Compare0Instr(name: String, operator: String, type: String) :
    CompareInstr(name, operator, type, null) {

    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val i1 = stack.pop()!!
        val i0 = stack.pop()!! as Comparable<Number>
        stack.add(if (impl(i0.compareTo(i1))) 1 else 0)
        return null
    }
}