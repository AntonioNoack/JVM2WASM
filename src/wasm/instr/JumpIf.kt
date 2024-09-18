package wasm.instr

class JumpIf(val label: String) : Instruction {
    override fun toString(): String {
        return "br_if $label"
    }
}