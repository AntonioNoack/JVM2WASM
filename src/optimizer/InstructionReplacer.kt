package optimizer

import me.anno.utils.algorithms.Recursion
import wasm.instr.*
import wasm.parser.FunctionImpl

fun interface InstructionReplacer {

    fun process(function: FunctionImpl) {
        process(function.body)
    }

    fun process(function: ArrayList<Instruction>) {
        Recursion.collectRecursive(function) { instructions, remaining ->
            processInstructions(instructions)
            for (i in instructions.indices) {
                when (val instr = instructions[i]) {
                    is IfBranch -> {
                        remaining.add(instr.ifTrue)
                        remaining.add(instr.ifFalse)
                    }
                    is LoopInstr -> remaining.add(instr.body)
                    is BlockInstr -> remaining.add(instr.body)
                    is SwitchCase -> remaining.addAll(instr.cases)
                    is BreakableInstruction -> throw NotImplementedError()
                }
            }
        }
    }

    fun processInstructions(instructions: ArrayList<Instruction>)

}