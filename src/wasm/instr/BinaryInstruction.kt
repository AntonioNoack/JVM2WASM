package wasm.instr

import wasm.writer.Opcode

abstract class BinaryInstruction(
    name: String, val popType: String, val pushType: String, val cppOperator: String, opcode: Opcode
) : SimpleInstr(name, opcode)