package wasm.instr

import graphing.GraphingNode
import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.any2
import translator.JavaTypes.typeListEquals
import utils.StringBuilder2
import wasm.instr.Instruction.Companion.appendParams
import wasm.instr.Instruction.Companion.appendResults
import wasm2cpp.instr.BreakThisLoopInstr

data class LoopInstr(
    override val label: String, var body: ArrayList<Instruction>,
    override val params: List<String>, override val results: List<String>
) : Instruction, BreakableInstruction {

    var typeIndex = -1

    constructor(label: String, node: GraphingNode) :
            this(label, node.printer.instrs, node.inputStack, node.outputStack)

    fun withBody(body: ArrayList<Instruction>): LoopInstr {
        return LoopInstr(label, body, params, results)
    }

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
        appendParams(params, builder) // todo do we ever need/use them?
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
        val lastInstr = body.lastOrNull { it !is Comment }
        if (lastInstr is Jump && lastInstr.label == label) return true // while(true)-loop
        if (lastInstr is BreakThisLoopInstr) return false // cancels loop (not WASM-compatible!)
        return body.any2 { it.isReturning() }
    }

    override fun equals(other: Any?): Boolean {
        return other === this ||
                other is LoopInstr &&
                other.label == label &&
                typeListEquals(other.params, params) &&
                typeListEquals(other.results, results) &&
                other.body == body
    }

    override fun hashCode(): Int {
        return label.hashCode()
    }

    override fun execute(engine: WASMEngine): String? {
        while (true) {
            val label1 = engine.executeInstructions(body)
            if (label1 == label) continue
            return label1
        }
    }
}
