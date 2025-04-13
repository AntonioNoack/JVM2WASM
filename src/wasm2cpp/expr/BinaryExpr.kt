package wasm2cpp.expr

import wasm.instr.Instruction

data class BinaryExpr(
    val instr: Instruction, val compA: Expr, val compB: Expr,
    override val jvmType: String
) : Expr