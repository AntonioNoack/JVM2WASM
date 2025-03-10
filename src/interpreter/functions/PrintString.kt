package interpreter.functions

import interpreter.WASMEngine
import wasm.instr.Instruction

object PrintString : Instruction {
    override fun execute(engine: WASMEngine): String? {
        val logNotErr = engine.pop() as Int
        val message = engine.str(engine.pop() as Int)
        val stream = if (logNotErr != 0) System.out else System.err
        stream.println(message)
        return null
    }
}