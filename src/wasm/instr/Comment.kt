package wasm.instr

import interpreter.WASMEngine

data class Comment(val text: String) : Instruction {
    override fun toString(): String = ";; $text"
    override fun execute(engine: WASMEngine): String? = null
}