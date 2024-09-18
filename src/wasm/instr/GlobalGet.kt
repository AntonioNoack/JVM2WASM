package wasm.instr

class GlobalGet(val name: String) : Instruction {
    override fun toString(): String = "global.get $name"
}