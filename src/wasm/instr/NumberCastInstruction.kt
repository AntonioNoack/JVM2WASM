package wasm.instr

class NumberCastInstruction(name: String, val prefix: String, val suffix: String, val popType: String) : SimpleInstr(name) {
    val pushType = name.substring(0, 3)
}