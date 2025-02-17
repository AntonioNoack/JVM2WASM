package wasm.instr

class Comment(val name: String) : Instruction {
    override fun toString(): String = ";; $name"
}