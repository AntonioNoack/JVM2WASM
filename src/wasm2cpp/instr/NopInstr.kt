package wasm2cpp.instr

import interpreter.WASMEngine
import wasm.instr.Instruction

object NopInstr : Instruction {
    override fun execute(engine: WASMEngine): String? {
        throw NotImplementedError()
    }
}