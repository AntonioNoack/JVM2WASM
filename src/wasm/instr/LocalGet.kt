package wasm.instr

class LocalGet(val name: String) : Instruction {
    override fun toString(): String = "local.get $name"
}