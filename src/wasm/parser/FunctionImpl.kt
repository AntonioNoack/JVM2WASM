package wasm.parser

import utils.StringBuilder2
import wasm.instr.Instruction
import wasm.instr.Instruction.Companion.appendParams
import wasm.instr.Instruction.Companion.appendResults

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
        appendParams(params, builder)
        appendResults(results, builder)
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