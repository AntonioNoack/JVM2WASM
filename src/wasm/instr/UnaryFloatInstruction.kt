package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop
import utils.WASMTypes.f32

class UnaryFloatInstruction(name: String, type: String, call: String, val impl: (Double) -> Double) :
    UnaryInstruction(name, type, type, call) {

    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val a = stack.pop()!!.toDouble()
        val b = impl(a)
        stack.add(if (popType == f32) b.toFloat() else b)
        return null
    }
}