package wasm.instr

data class GlobalSet(val name: String) : Instruction {

    init {
        if (name.startsWith('$')) throw IllegalArgumentException(name)
    }

    override fun toString(): String = "global.set \$$name"
}