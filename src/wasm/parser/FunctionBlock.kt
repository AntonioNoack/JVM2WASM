package wasm.parser

import wasm.instr.Instruction

data class FunctionBlock(val nextI: Int, val instructions: List<Instruction>)