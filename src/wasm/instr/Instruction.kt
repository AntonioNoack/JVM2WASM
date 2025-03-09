package wasm.instr

import utils.Param
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
                for (param in params) builder.append(" ").append(param)
                builder.append(")")
            }
        }

        fun appendParams1(params: List<Param>, builder: StringBuilder2) {
            if (params.isNotEmpty()) {
                builder.append(" (param")
                for (param in params) builder.append(" ").append(param.wasmType)
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