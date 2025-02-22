package wasm.instr

import utils.StringBuilder2

class CallIndirect(val type: FuncType) : Instruction {
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
}