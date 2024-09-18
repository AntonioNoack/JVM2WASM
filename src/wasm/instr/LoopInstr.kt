package wasm.instr

class LoopInstr(val label: String, val body: List<Instruction>, val results: List<String>) : Instruction
