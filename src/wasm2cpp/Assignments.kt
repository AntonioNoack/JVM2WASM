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

    fun findAssignments(instructions: List<Instruction>): Map<String, Int> {
        val result = HashMap<String, Int>()
        for (i in instructions.indices) {
            val instr = instructions[i]
            insertAssignments(instr, i, result)
        }
        return result
    }

    private fun insertAssignments(instr: Instruction, i: Int, result: HashMap<String, Int>) {
        when (instr) {
            is LocalSet -> insertAssignment(instr.name, i, result)
            is ParamSet -> insertAssignment(instr.name, i, result)
            is GlobalSet -> insertAssignment(instr.name, i, result)
            is LoopInstr -> insertAssignments(instr.body, i, result)
            is IfBranch -> {
                insertAssignments(instr.ifTrue, i, result)
                insertAssignments(instr.ifFalse, i, result)
            }
            is Call, is CallIndirect -> {
                // todo we don't need that, if we can prove that the function is pure wrt writing memory
                //  (not native, not setting, not calling any setting methods)
                insertAssignment(MEMORY_DEPENDENCY, i, result)
            }
            is StoreInstr -> {
                insertAssignment(MEMORY_DEPENDENCY, i, result)
                // be field-specific???
            }
            is SimpleInstr, is Const, is Jump, is JumpIf, is Comment,
            is LocalGet, is ParamGet, is GlobalGet -> {
                // nothing to do
            }
            PtrDupInstr -> {}
            is HighLevelInstruction -> {
                for (child in instr.toLowLevel()) {
                    insertAssignments(child, i, result)
                }
            }
            else -> throw NotImplementedError("Unknown instruction ${instr.javaClass}")
        }
    }

    private fun insertAssignment(name: String, i: Int, result: HashMap<String, Int>) {
        result[name] = i
    }

    private fun insertAssignments(instructions: List<Instruction>, i: Int, result: HashMap<String, Int>) {
        for (j in instructions.indices) {
            insertAssignments(instructions[j], i, result)
        }
    }
}