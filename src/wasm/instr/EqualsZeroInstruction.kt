package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop
import utils.WASMTypes.i32

class EqualsZeroInstruction(name: String, type: String, call: String) :
    UnaryInstruction(name, type, i32, call) {

    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val a = stack.pop()!!.toLong()
        stack.add(if (a == 0L) 1 else 0)
        return null
    }
}