package wasm.instr

class LocalSet(val name: String) : Instruction {
    override fun toString(): String = "local.set $name"
}