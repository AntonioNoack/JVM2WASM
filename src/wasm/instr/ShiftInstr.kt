package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop

class ShiftInstr(name: String, val impl: (Number, Int) -> Number) : SimpleInstr(name) {
    val type = name.substring(0, 3)
    val isRight get() = name[6] == 'r'
    val isU get() = name[8] == 'u'

    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val i1 = stack.pop()!!.toInt()
        val i0 = stack.pop()!!
        stack.add(impl(i0, i1))
        return null
    }
}