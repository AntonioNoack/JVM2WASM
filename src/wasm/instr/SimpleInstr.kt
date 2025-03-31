package wasm.instr

import wasm.writer.Opcode

abstract class SimpleInstr(val name: String, val opcode: Opcode) : Instruction {
    init {
        simpleInstructions[name] = this
    }

    override fun toString(): String = name

    companion object {
        val simpleInstructions = HashMap<String, SimpleInstr>(64)

        init {
            // ensure all instructions are loaded
            Instructions.I64_ROTR
            Drop
        }
    }
}