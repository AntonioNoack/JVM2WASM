package wasm.instr

class UnaryInstruction2(name: String, val prefix: String, val suffix: String, val popType: String) : SimpleInstr(name) {
    val pushType = name.substring(0, 3)
}