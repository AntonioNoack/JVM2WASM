package interpreter.functions

import interpreter.WASMEngine
import wasm.instr.Instruction

object PrintStackTraceLine2 : Instruction {
    override fun execute(engine: WASMEngine): String? {
        // depth, class, method, line
        val line = engine.pop() as Int
        val method = engine.str(engine.pop().toInt())
        val clazz = engine.str(engine.pop().toInt())
        println("  $clazz.$method:$line")
        return null
    }
}