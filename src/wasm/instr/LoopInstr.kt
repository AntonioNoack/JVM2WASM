package wasm.instr

import utils.StringBuilder2

class LoopInstr(val label: String, val body: List<Instruction>, val results: List<String>) : Instruction {

    init {
        if (label.startsWith('$')) throw IllegalArgumentException(label)
    }

    override fun toString(): String {
        val builder = StringBuilder2()
        toString(0, builder)
        return builder.toString()
    }

    override fun toString(depth: Int, builder: StringBuilder2) {
        for (i in 0 until depth) builder.append("  ")
        builder.append("(loop \$").append(label)
        if (results.isNotEmpty()) {
            builder.append(" (result")
            for (result in results) builder.append(" ").append(result)
            builder.append(")")
        }
        builder.append("\n")
        for (instr in body) {
            instr.toString(depth + 1, builder)
            builder.append("\n")
        }
        for (i in 0 until depth) builder.append("  ")
        builder.append(")")
    }
}
