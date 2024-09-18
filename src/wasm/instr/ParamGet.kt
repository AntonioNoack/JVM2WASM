package wasm.instr

class ParamGet(val index: Int) : Instruction {
    val name = "p$index"
    override fun toString(): String = "local.get $index"
}