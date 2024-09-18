package wasm.instr

class GlobalSet(val name: String) : Instruction {
    override fun toString(): String = "global.set $name"
}