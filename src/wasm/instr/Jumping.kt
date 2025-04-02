package wasm.instr

abstract class Jumping(var owner: BreakableInstruction) : Instruction {

    var depth = -1
    val label get() = owner.label

    init {
        if (label.startsWith('$')) throw IllegalArgumentException(label)
    }
}