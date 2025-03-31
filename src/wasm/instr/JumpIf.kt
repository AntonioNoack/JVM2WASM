package wasm.instr

import interpreter.WASMEngine

class JumpIf(var owner: BreakableInstruction) : Instruction {

    var depth = -1

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

    override fun execute(engine: WASMEngine): String? {
        val flag = engine.pop() as Int
        return if (flag != 0) label else null
    }
}