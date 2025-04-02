package optimizer

import wasm.instr.Instruction

fun interface InstructionProcessor : InstructionReplacer {

    override fun processInstructions(instructions: ArrayList<Instruction>) {
        for (i in instructions.indices) {
            processInstruction(instructions[i])
        }
    }

    fun processInstruction(instruction: Instruction)
}