package wasm.instr

import interpreter.WASMEngine
import utils.StringBuilder2
import wasm.instr.Instruction.Companion.appendParams
import wasm.instr.Instruction.Companion.appendResults

/**
 * like LoopInstr, except that Jump jumps to the end;
 * */
@Deprecated("Hard to handle in C++ regarding the stack, and hard to handle regarding isReturning()")
data class BlockInstr(
    override val label: String, var body: ArrayList<Instruction>,
    override val params: List<String>, override val results: List<String>
) : Instruction, BreakableInstruction {

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
        builder.append("(block \$").append(label)
        appendParams(params, builder)
        appendResults(results, builder)
        builder.append("\n")
        for (instr in body) {
            instr.toString(depth + 1, builder)
            builder.append("\n")
        }
        for (i in 0 until depth) builder.append("  ")
        builder.append(")")
    }

    override fun isReturning(): Boolean {
        // idk, we need to check all branches...
        // to do implement this properly...
        //  this returns if any instruction within returns;
        //  except that jumps to this' label return locally, but don't make this returning 🤔
        return false
    }

    override fun equals(other: Any?): Boolean {
        return other === this ||
                other is BlockInstr &&
                other.label == label &&
                other.params == params &&
                other.results == results &&
                other.body == body
    }

    override fun hashCode(): Int {
        return label.hashCode()
    }

    override fun execute(engine: WASMEngine): String? {
        val label1 = engine.executeInstructions(body)
        return if (label1 == label) null else label1
    }
}
