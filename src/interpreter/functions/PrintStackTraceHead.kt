package interpreter.functions

import interpreter.WASMEngine
import wasm.instr.Instruction

object PrintStackTraceHead : Instruction {
    override fun execute(engine: WASMEngine): String? {
        val message = engine.str(engine.pop().toInt())
        val name = engine.str(engine.pop().toInt())
        println("$name: $message")
        return null
    }
}