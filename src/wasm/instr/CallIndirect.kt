package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop
import utils.StringBuilder2

data class CallIndirect(val type: FuncType) : Instruction {
    override fun toString(): String {
        val builder = StringBuilder2()
        toString(0, builder)
        return builder.toString()
    }

    override fun toString(depth: Int, builder: StringBuilder2) {
        for (i in 0 until depth) builder.append("  ")
        builder.append("call_indirect (type \$")
        type.toString(builder)
        builder.append(")")
    }

    override fun execute(engine: WASMEngine): String? {
        val index = engine.pop() as Int
        val function = engine.functionTable[index]
        // todo verify type
        engine.executeFunction(function)
        return null
    }
}