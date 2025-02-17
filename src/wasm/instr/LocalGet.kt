package wasm.instr

class LocalGet(val name: String) : Instruction {
    constructor(idx: Int) : this(idx.toString())

    override fun toString(): String = "local.get $name"
}