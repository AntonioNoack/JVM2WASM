package wasm.instr

class JumpIf(var owner: BreakableInstruction) : Instruction {

    init {
        if (label.startsWith('$')) throw IllegalArgumentException(label)
    }

    val label get() = owner.label
    override fun toString(): String = "br_if \$$label"

    override fun equals(other: Any?): Boolean {
        return other is JumpIf && other.label == label
    }

    override fun hashCode(): Int {
        return label.hashCode()
    }
}