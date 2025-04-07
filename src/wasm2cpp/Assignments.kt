package wasm2cpp

import highlevel.HighLevelInstruction
import highlevel.PtrDupInstr
import wasm.instr.*

object Assignments {

    const val MEMORY_DEPENDENCY = "<mem>"

    fun hasAssignment(assignments: Map<String, Int>?, name: String, i: Int): Boolean {
        if (assignments == null) return true // we must assume the worst
        val lastLine = assignments[name]
            ?: return false // not modified at all
        return i <= lastLine
    }

    fun findAssignments(instructions: List<Instruction>, pureFunctions: Set<String>): Map<String, Int> {
        val result = HashMap<String, Int>()
        for (i in instructions.indices) {
            val instr = instructions[i]
            insertAssignments(instr, i, result, pureFunctions)
        }
        return result
    }

    private fun insertAssignments(
        instr: Instruction, i: Int, result: HashMap<String, Int>,
        pureFunctions: Set<String>
    ) {
        when (instr) {
            is LocalSet -> insertAssignment(instr.name, i, result)
            is ParamSet -> insertAssignment(instr.name, i, result)
            is GlobalSet -> insertAssignment(instr.name, i, result)
            is LoopInstr -> insertAssignments(instr.body, i, result, pureFunctions)
            is IfBranch -> {
                insertAssignments(instr.ifTrue, i, result, pureFunctions)
                insertAssignments(instr.ifFalse, i, result, pureFunctions)
            }
            is Call -> {
                // we don't need that, if we can prove that the function is pure wrt writing memory
                //  (not native, not setting, not calling any setting methods)
                if (instr.name !in pureFunctions) {
                    insertAssignment(MEMORY_DEPENDENCY, i, result)
                }
            }
            is CallIndirect -> insertAssignment(MEMORY_DEPENDENCY, i, result)
            is StoreInstr -> {
                insertAssignment(MEMORY_DEPENDENCY, i, result)
                // be field-specific???
            }
            is SimpleInstr, is Const, is Jump, is JumpIf, is Comment,
            is LocalGet, is ParamGet, is GlobalGet -> {
                // nothing to do
            }
            PtrDupInstr -> {}
            is HighLevelInstruction -> insertAssignments(instr.toLowLevel(), i, result, pureFunctions)
            else -> throw NotImplementedError("Unknown instruction ${instr.javaClass}")
        }
    }

    private fun insertAssignment(name: String, i: Int, result: HashMap<String, Int>) {
        result[name] = i
    }

    private fun insertAssignments(
        instructions: List<Instruction>, i: Int,
        result: HashMap<String, Int>, pureFunctions: Set<String>
    ) {
        for (j in instructions.indices) {
            insertAssignments(instructions[j], i, result, pureFunctions)
        }
    }
}