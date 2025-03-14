package interpreter.functions

import interpreter.WASMEngine
import wasm.instr.Instruction

object PrintStackTraceHead : Instruction {
    override fun execute(engine: WASMEngine): String? {
        val message = engine.readString(engine.pop())
        val name = engine.readString(engine.pop())
        println("$name: $message")
        return null
    }
}