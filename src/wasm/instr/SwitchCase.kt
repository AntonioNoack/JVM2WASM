package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.lists.Lists.all2
import me.anno.utils.structures.lists.Lists.any2
import utils.StringBuilder2

data class SwitchCase(
    override val label: String, var cases: List<List<Instruction>>,
    override val params: List<String>, override val results: List<String>,
) : Instruction, BreakableInstruction {

    init {
        assertTrue(params.isEmpty())
        assertTrue(results.isEmpty())
    }

    override fun toString(): String {
        val builder = StringBuilder2()
        toString(0, builder)
        return builder.toString()
    }

    override fun toString(depth: Int, builder: StringBuilder2) {
        // (block (block (block (block (block (block (block (block (block
        //  (block local.get $lbl (br_table 0 1 2 3 4 5 6 7 8))
        for (i in 0 until depth) builder.append("  ")
        for (i in 0..cases.size) builder.append("(block ")
        builder.append("local.get \$").append(label).append(" (br_table")
        for (i in cases.indices) builder.append(" ").append(i)
        builder.append("))\n")
        for (i in cases.indices) {
            val di = depth + 1 // cases.lastIndex - i
            for (instr in cases[i]) {
                instr.toString(di, builder)
                builder.append("\n")
            }
            for (j in 0 until di) builder.append("  ")
            builder.append(")\n")
        }
        builder.size-- // remove last line-break
    }

    override fun isReturning(): Boolean {
        return cases.all2 { instructions ->
            instructions.any2 { instr -> instr.isReturning() }
        }
    }

    override fun equals(other: Any?): Boolean {
        return other === this ||
                other is SwitchCase &&
                other.label == label &&
                other.params == params &&
                other.results == results &&
                other.cases == cases
    }

    override fun hashCode(): Int {
        return label.hashCode()
    }

    override fun execute(engine: WASMEngine): String? {
        // todo get label-variable
        // todo execute that branch
        TODO("Not yet implemented")
    }
}