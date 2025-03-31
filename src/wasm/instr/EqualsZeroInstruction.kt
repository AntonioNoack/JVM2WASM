package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop
import utils.WASMTypes.i32
import wasm.writer.Opcode

class EqualsZeroInstruction(name: String, type: String, call: String, opcode: Opcode) :
    UnaryInstruction(name, type, i32, call, opcode) {

    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val a = stack.pop()!!.toLong()
        stack.add(if (a == 0L) 1 else 0)
        return null
    }
}