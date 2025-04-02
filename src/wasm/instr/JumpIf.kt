package wasm.instr

import interpreter.WASMEngine

class JumpIf(owner: BreakableInstruction) : Jumping(owner) {

    override fun toString(): String = "br_if \$$label"

    override fun execute(engine: WASMEngine): String? {
        val flag = engine.pop() as Int
        return if (flag != 0) label else null
    }

    override fun equals(other: Any?): Boolean {
        return other is JumpIf && other.label == label
    }

    override fun hashCode(): Int {
        return label.hashCode()
    }
}