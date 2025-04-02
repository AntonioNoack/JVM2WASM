package wasm.parser

import wasm.instr.Instruction

data class FunctionBlock(val nextI: Int, val instructions: ArrayList<Instruction>)