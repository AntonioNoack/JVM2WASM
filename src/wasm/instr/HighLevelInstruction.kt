package wasm.instr

interface HighLevelInstruction: Instruction {
    fun toLowLevel(): List<Instruction>
}