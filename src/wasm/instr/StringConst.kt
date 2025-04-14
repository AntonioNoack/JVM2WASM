package wasm.instr

import highlevel.HighLevelInstruction
import interpreter.WASMEngine
import jvm.JVMFlags.is32Bits
import wasm.instr.Const.Companion.ptrConst

data class StringConst(val string: String, val address: Int) : HighLevelInstruction() {
    override fun execute(engine: WASMEngine): String? {
        engine.push(if (is32Bits) address else address.toLong())
        return null
    }

    override fun toLowLevel(): List<Instruction> {
        return listOf(ptrConst(address))
    }
}