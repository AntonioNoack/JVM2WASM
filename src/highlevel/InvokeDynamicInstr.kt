package highlevel

import utils.MethodSig
import wasm.instr.Instruction

class InvokeDynamicInstr(val sig: MethodSig) : HighLevelInstruction() {
    override fun toLowLevel(): List<Instruction> {
        TODO("Not yet implemented")
    }
}