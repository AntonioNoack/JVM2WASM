package wasm.instr

import interpreter.WASMEngine
import utils.WASMTypes.i32

class BinaryI32Instruction(name: String, cppOperator: String, val impl: (Int, Int) -> Int) :
    BinaryInstruction(name, i32, i32, cppOperator) {
    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val i1 = stack.removeLast() as Int
        val i0 = stack.removeLast() as Int
        stack.add(impl(i0, i1))
        return null
    }
}