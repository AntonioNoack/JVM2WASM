package wasm.instr

class IfBranch(
    val ifTrue: List<Instruction>, val ifFalse: List<Instruction>,
    val params: List<String>, val results: List<String>
) : Instruction