package interpreter.functions

import interpreter.WASMEngine
import interpreter.WASMEngine.Companion.RETURN_LABEL
import wasm.instr.Instruction

object GetAllocatedSize : Instruction {
    override fun execute(engine: WASMEngine): String {
        engine.stack.add(engine.buffer.capacity())
        return RETURN_LABEL
    }
}