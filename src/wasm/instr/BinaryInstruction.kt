package wasm.instr

class BinaryInstruction(name: String, val cppOperator: String) : SimpleInstr(name) {
    val type = name.substring(0, 3)
}