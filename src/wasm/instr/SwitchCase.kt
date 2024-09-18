package wasm.instr

class SwitchCase(val cases: List<List<Instruction>>) : Instruction {
    override fun toString(): String = "switchCase[${cases.size}]"
}