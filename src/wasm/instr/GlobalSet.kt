package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop

data class GlobalSet(val name: String) : Instruction {

    var index = -1

    init {
        if (name.startsWith('$')) throw IllegalArgumentException(name)
    }

    override fun toString(): String = "global.set \$$name"
    override fun execute(engine: WASMEngine): String? {
        engine.globals[name] = engine.stack.pop()!!
        return null
    }
}