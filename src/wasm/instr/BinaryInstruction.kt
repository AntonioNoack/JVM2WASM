package wasm.instr

class BinaryInstruction(name: String, val operator: String) : SimpleInstr(name) {
    val type = name.substring(0, 3)
}