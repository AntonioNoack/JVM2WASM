package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop

class CompareU32Instr(name: String, operator: String, type: String) :
    CompareInstr(name, operator, type, "u32") {

    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val i1 = (stack.pop() as Int).toUInt()
        val i0 = (stack.pop() as Int).toUInt()
        stack.add(if (impl(i0.compareTo(i1))) 1 else 0)
        return null
    }
}