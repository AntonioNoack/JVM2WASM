package wasm.instr

import utils.StringBuilder2

data class IfBranch(
    var ifTrue: List<Instruction>, var ifFalse: List<Instruction>,
    val params: List<String>, val results: List<String>
) : Instruction {

    constructor(ifTrue: List<Instruction>) : this(ifTrue, emptyList(), emptyList(), emptyList())

    override fun toString(): String {
        val builder = StringBuilder2()
        toString(0, builder)
        return builder.toString()
    }

    override fun toString(depth: Int, builder: StringBuilder2) {
        for (i in 0 until depth) builder.append("  ")
        builder.append("(if")
        if (params.isNotEmpty()) {
            builder.append(" (param")
            for (param in params) builder.append(" ").append(param)
            builder.append(")")
        }
        if (results.isNotEmpty()) {
            builder.append(" (result")
            for (result in results) builder.append(" ").append(result)
            builder.append(")")
        }
        builder.append(" (then\n")
        for (instr in ifTrue) {
            instr.toString(depth + 1, builder)
            builder.append("\n")
        }
        for (i in 0 until depth) builder.append("  ")
        if (ifFalse.isEmpty()) {
            builder.append("))")
        } else {
            builder.append(") (else\n")
            for (instr in ifFalse) {
                instr.toString(depth + 1, builder)
                builder.append("\n")
            }
            for (i in 0 until depth) builder.append("  ")
            builder.append("))")
        }
    }

    override fun isReturning(): Boolean {
        val trueReturns = ifTrue.lastOrNull { it !is Comment }?.isReturning() ?: false
        val falseReturns = ifFalse.lastOrNull { it !is Comment }?.isReturning() ?: false
        return trueReturns && falseReturns
    }
}