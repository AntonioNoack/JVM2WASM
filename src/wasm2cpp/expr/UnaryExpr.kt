package wasm2cpp.expr

import wasm.instr.Instruction

data class UnaryExpr(
    val instr: Instruction, val input: Expr,
    override val jvmType: String
) : Expr