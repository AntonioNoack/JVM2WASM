package wasm.instr

import interpreter.WASMEngine

data class Comment(val name: String) : Instruction {
    override fun toString(): String = ";; $name"
    override fun execute(engine: WASMEngine): String? = null
}