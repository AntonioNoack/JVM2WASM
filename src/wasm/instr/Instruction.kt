package wasm.instr

import utils.StringBuilder2

interface Instruction {
    fun toString(depth: Int, builder: StringBuilder2) {
        for (i in 0 until depth) builder.append("  ")
        builder.append(toString())
    }
}