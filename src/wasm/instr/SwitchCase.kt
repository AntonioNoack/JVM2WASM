package wasm.instr

class SwitchCase(val cases: List<List<Instruction>>) : Instruction {

    override fun toString(): String {
        val builder = StringBuilder()
        toString(0, builder)
        return builder.toString()
    }

    override fun toString(depth: Int, builder: StringBuilder) {
        // (block (block (block (block (block (block (block (block (block
        //  (block local.get $lbl (br_table 0 1 2 3 4 5 6 7 8))
        for (i in 0 until depth) builder.append("  ")
        for (i in 0 .. cases.size) builder.append("(block ")
        builder.append("local.get \$lbl (br_table")
        for (i in cases.indices) builder.append(" ").append(i)
        builder.append(")\n")
        for (i in cases.indices) {
            val di = depth + cases.size - i
            for (instr in cases[i]) {
                instr.toString(di, builder)
                builder.append("\n")
            }
            for (j in 0 until di) builder.append("  ")
            builder.append(")\n")
        }
        for (j in 0 until depth) builder.append("  ")
        builder.append(")\n")
    }
}