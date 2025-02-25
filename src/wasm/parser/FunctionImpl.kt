package wasm.parser

import utils.StringBuilder2
import wasm.instr.Instruction

open class FunctionImpl(
    val funcName: String, val params: List<String>, val results: List<String>,
    val locals: List<LocalVariable>,
    var body: List<Instruction>,
    val isExported: Boolean
) {
    override fun toString(): String {
        val builder = StringBuilder2()
            .append("(func \$").append(funcName)
        if (isExported) {
            builder.append(" (export \"").append(funcName).append("\")")
        }
        if (params.isNotEmpty()) {
            builder.append(" (param")
            for (param in params) {
                builder.append(' ').append(param)
            }
            builder.append(")")
        }
        if (results.isNotEmpty()) {
            builder.append(" (result")
            for (result in results) {
                builder.append(' ').append(result)
            }
            builder.append(")")
        }
        builder.append("\n")
        for (local in locals) {
            builder.append("  (local \$").append(local.name).append(' ').append(local.type).append(")\n")
        }
        for (instr in body) {
            instr.toString(1, builder)
            builder.append("\n")
        }
        builder.append(")\n")
        return builder.toString()
    }
}