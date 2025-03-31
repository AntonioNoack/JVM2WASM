package wasm.writer

import wasm.instr.Instruction

class Global(val name: String, val initExpr: List<Instruction>, val type: Type, val mutable: Boolean)