package wasm.instr

data class Jump(val label: String) : Instruction {

    init {
        if (label.startsWith('$')) throw IllegalArgumentException(label)
    }

    override fun toString(): String = "br \$$label"
    override fun isReturning(): Boolean = true
}