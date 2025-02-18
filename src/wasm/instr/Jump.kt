package wasm.instr

class Jump(val label: String) : Instruction {

    init {
        if (label.startsWith('$')) throw IllegalArgumentException(label)
    }

    override fun toString(): String = "br \$$label"
}