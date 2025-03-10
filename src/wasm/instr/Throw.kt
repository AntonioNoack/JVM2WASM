package wasm.instr

import interpreter.WASMEngine

class Throw(val tag: String) : Instruction {

    init {
        if (tag.startsWith('$')) throw IllegalArgumentException(tag)
    }

    override fun toString(): String = "throw \$$tag"
    override fun isReturning(): Boolean = true
    override fun execute(engine: WASMEngine) = tag

}