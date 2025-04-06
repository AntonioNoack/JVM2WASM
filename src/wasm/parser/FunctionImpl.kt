package wasm.parser

import utils.Param
import utils.StringBuilder2
import wasm.instr.Instruction
import wasm.instr.Instruction.Companion.appendParams1
import wasm.instr.Instruction.Companion.appendResults

open class FunctionImpl(
    val funcName: String, val params: List<Param>, val results: List<String>,
    var locals: List<LocalVariable>, var body: ArrayList<Instruction>, val isExported: Boolean
) {

    var index = -1

    override fun toString(): String {
        val builder = StringBuilder2()
        toString(builder)
        return builder.toString()
    }

    fun toString(builder: StringBuilder2) {
        builder
            .append("(func \$").append(funcName)
        if (isExported) {
            builder.append(" (export \"").append(funcName).append("\")")
        }
        appendParams1(params, builder)
        appendResults(results, builder)
        builder.append("\n")
        for (local in locals) {
            builder.append("  (local \$").append(local.name).append(' ').append(local.wasmType).append(")\n")
        }
        for (instr in body) {
            instr.toString(1, builder)
            builder.append("\n")
        }
        builder.append(")\n")
    }

    fun withBody(newBody: ArrayList<Instruction>): FunctionImpl {
        return FunctionImpl(
            funcName, params, results,
            locals, newBody, isExported
        )
    }
}