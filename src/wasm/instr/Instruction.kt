package wasm.instr

interface Instruction {
    fun toString(depth: Int, builder: StringBuilder) {
        for (i in 0 until depth) builder.append("  ")
        builder.append(toString())
    }
}