package wasm.instr

abstract class UnaryInstruction(name: String, val popType: String, val pushType: String, val call: String) : SimpleInstr(name)