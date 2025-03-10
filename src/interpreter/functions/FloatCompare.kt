package interpreter.functions

import interpreter.WASMEngine
import wasm.instr.Instruction

class FloatCompare(private val ifNan: Int) : Instruction {
    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val i1 = engine.pop().toDouble()
        val i0 = engine.pop().toDouble()
        stack.add(compare(i0, i1))
        return null
    }

    private fun compare(i0: Double, i1: Double): Int {
        if (i0.isNaN() || i1.isNaN()) return ifNan
        return i0.compareTo(i1)
    }
}