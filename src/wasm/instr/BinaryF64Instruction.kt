package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop
import utils.WASMTypes.f64

class BinaryF64Instruction(name: String, cppOperator: String, val impl: (Double, Double) -> Double) :
    BinaryInstruction(name, f64, f64, cppOperator) {
    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val i1 = stack.pop() as Double
        val i0 = stack.pop() as Double
        stack.add(impl(i0, i1))
        return null
    }
}