package wasm.instr

abstract class BinaryInstruction(name: String, val type: String, val cppOperator: String) : SimpleInstr(name)