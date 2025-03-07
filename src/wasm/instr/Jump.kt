package wasm.instr

class Jump(var owner: BreakableInstruction) : Instruction {

    init {
        if (label.startsWith('$')) throw IllegalArgumentException(label)
    }

    val label get() = owner.label
    override fun toString(): String = "br \$$label"
    override fun isReturning(): Boolean = true

    override fun equals(other: Any?): Boolean {
        return other is Jump && other.label == label
    }

    override fun hashCode(): Int {
        return label.hashCode()
    }
}