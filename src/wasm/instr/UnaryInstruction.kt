package wasm.instr

import wasm.writer.Opcode

abstract class UnaryInstruction(
    name: String, val popType: String, val pushType: String,
    val call: String, opcode: Opcode
) : SimpleInstr(name, opcode)