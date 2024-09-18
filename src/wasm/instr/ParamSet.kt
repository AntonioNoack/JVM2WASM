package wasm.instr

class ParamSet(val index: Int) : Instruction {
    val name = "p$index"
    override fun toString(): String = "local.set $index"
}