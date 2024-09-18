package wasm.instr

class Call(val name: String) : Instruction {
    override fun toString(): String = "call $name"
}