package wasm.parser

import wasm.instr.Instruction

data class FunctionBlock(val i: Int, val instructions: List<Instruction>)