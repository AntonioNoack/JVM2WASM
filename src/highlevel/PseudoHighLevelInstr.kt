package highlevel

import wasm.instr.Instruction

/**
 * just for testing things
 * */
class PseudoHighLevelInstr(val body: List<Instruction>) : HighLevelInstruction() {
    override fun toLowLevel(): List<Instruction> {
        return body
    }
}