package wasm.instr

class Throw(val tag: String) : Instruction {

    init {
        if (tag.startsWith('$')) throw IllegalArgumentException(tag)
    }

    override fun toString(): String = "throw \$$tag"
}