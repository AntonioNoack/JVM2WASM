package wasm.instr

data class LocalGet(var name: String) : Instruction {

    init {
        if (name.startsWith('$')) throw IllegalArgumentException(name)
    }

    override fun toString(): String = "local.get \$$name"
}