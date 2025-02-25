package wasm.instr

data class LocalSet(val name: String) : Instruction {

    init {
        if (name.startsWith('$')) throw IllegalArgumentException(name)
    }

    override fun toString(): String = "local.set \$$name"
}