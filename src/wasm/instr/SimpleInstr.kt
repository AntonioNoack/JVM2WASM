package wasm.instr

abstract class SimpleInstr(val name: String) : Instruction {
    init {
        simpleInstructions[name] = this
    }

    override fun toString(): String = name

    companion object {
        val simpleInstructions = HashMap<String, SimpleInstr>()

        init {
            // ensure all instructions are loaded
            Instructions.Drop
        }
    }
}