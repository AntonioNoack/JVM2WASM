package highlevel

import utils.InterfaceSig
import wasm.instr.Instruction

class InvokeInterfaceInstr(val sig: InterfaceSig): HighLevelInstruction() {
    override fun toLowLevel(): List<Instruction> {
        TODO("Not yet implemented")
    }
}