package wasm.instr

abstract class BinaryInstruction(name: String, val popType: String, val pushType: String, val cppOperator: String) : SimpleInstr(name)