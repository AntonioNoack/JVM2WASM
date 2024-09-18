package wasm.instr

class Jump(val label: String) : Instruction {
    override fun toString(): String {
        return "br $label"
    }
}