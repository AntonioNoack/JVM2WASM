package wasm.instr

class JumpIf(val label: String) : Instruction {

    init {
        if (label.startsWith('$')) throw IllegalArgumentException(label)
    }

    override fun toString(): String = "br_if \$$label"
}