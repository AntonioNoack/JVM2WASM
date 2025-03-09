package wasm.instr

data class LocalSet(var name: String) : Instruction {

    init {
        if (name.startsWith('$')) throw IllegalArgumentException(name)
    }

    override fun toString(): String = "local.set \$$name"
}