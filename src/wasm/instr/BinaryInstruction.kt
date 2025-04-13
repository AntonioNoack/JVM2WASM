package wasm.instr

import wasm.writer.Opcode

abstract class BinaryInstruction(
    name: String, val popType: String, val pushType: String, val operator: BinaryOperator, opcode: Opcode
) : SimpleInstr(name, opcode)