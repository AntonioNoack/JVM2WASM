package highlevel

import interpreter.WASMEngine
import jvm.JVMFlags.is32Bits
import wasm.instr.Call
import wasm.instr.Instruction

object PtrDupInstr : HighLevelInstruction() {
    override fun execute(engine: WASMEngine): String? {
        val value = engine.pop()
        engine.push(value)
        engine.push(value)
        return null
    }

    override fun toLowLevel(): List<Instruction> {
        return listOf(if (is32Bits) Call.dupI32 else Call.dupI64)
    }
}