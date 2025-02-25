package wasm.instr

data class Comment(val name: String) : Instruction {
    override fun toString(): String = ";; $name"
}