package wasm.instr

import utils.StringBuilder2

interface Instruction {
    fun toString(depth: Int, builder: StringBuilder2) {
        for (i in 0 until depth) builder.append("  ")
        builder.append(toString())
    }

    fun isReturning(): Boolean = false

    companion object {
        fun appendParams(params: List<String>, builder: StringBuilder2) {
            if (params.isNotEmpty()) {
                builder.append(" (param")
                for (result in params) builder.append(" ").append(result)
                builder.append(")")
            }
        }

        fun appendResults(results: List<String>, builder: StringBuilder2) {
            if (results.isNotEmpty()) {
                builder.append(" (result")
                for (result in results) builder.append(" ").append(result)
                builder.append(")")
            }
        }
    }
}