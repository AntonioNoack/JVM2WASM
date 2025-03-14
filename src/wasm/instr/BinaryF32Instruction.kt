package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop
import utils.WASMTypes.f32

class BinaryF32Instruction(name: String, cppOperator: String, val impl: (Float, Float) -> Float) :
    BinaryInstruction(name, f32, f32, cppOperator) {
    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val i1 = stack.pop() as Float
        val i0 = stack.pop() as Float
        stack.add(impl(i0, i1))
        return null
    }
}