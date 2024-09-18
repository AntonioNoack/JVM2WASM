package wasm.instr

class UnaryInstruction(name: String, val call: String) : SimpleInstr(name) {
    val type = name.substring(0, 3)
}