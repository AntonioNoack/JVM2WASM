package interpreter.functions

import interpreter.WASMEngine
import interpreter.WASMEngine.Companion.RETURN_LABEL
import me.anno.utils.structures.lists.Lists.pop
import wasm.instr.Instruction

class FloatCompare(val ifNan: Int) : Instruction {
    override fun execute(engine: WASMEngine): String {
        val stack = engine.stack
        val i0 = engine.getParam(0).toDouble()
        val i1 = engine.getParam(1).toDouble()
        stack.add(compare(i0, i1))
        return RETURN_LABEL
    }

    private fun compare(i0: Double, i1: Double): Int {
        if (i0.isNaN() || i1.isNaN()) return ifNan
        return i0.compareTo(i1)
    }
}