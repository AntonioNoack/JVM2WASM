package wasm.instr

import interpreter.WASMEngine

class Jump(owner: BreakableInstruction) : Jumping(owner) {

    override fun toString(): String = "br \$$label"
    override fun isReturning(): Boolean = true

    override fun execute(engine: WASMEngine) = label

    override fun equals(other: Any?): Boolean {
        return other is Jump && other.label == label
    }

    override fun hashCode(): Int {
        return label.hashCode()
    }
}