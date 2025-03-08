package wasm2cpp

import wasm.instr.*

object Assignments {

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
            is SwitchCase -> {
                for (j in instr.cases.indices) {
                    insertAssignments(instr.cases[j], i, result)
                }
            }
            is SimpleInstr, is Const, is Jump, is JumpIf, is Comment,
            is Call, is CallIndirect, is LocalGet, is ParamGet, is GlobalGet -> {
                // nothing to do
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