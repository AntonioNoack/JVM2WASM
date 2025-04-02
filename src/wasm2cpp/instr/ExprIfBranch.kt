package wasm2cpp.instr

import me.anno.utils.structures.lists.Lists.any2
import wasm.instr.Instruction
import wasm2cpp.StackElement

class ExprIfBranch(
    val expr: StackElement,
    val ifTrue: List<Instruction>,
    val ifFalse: List<Instruction>
) : CppInstruction {

    override fun isReturning(): Boolean {
        return ifTrue.any2 { it.isReturning() } && ifFalse.any2 { it.isReturning() }
    }
}