package interpreter.functions

import interpreter.WASMEngine
import wasm.instr.Instruction

object PrintStackTraceLine : Instruction {
    private var ctr = 0
    override fun execute(engine: WASMEngine): String? {
        // depth, class, method, line
        val line = engine.pop() as Int
        val method = engine.str(engine.pop().toInt())
        val clazz = engine.str(engine.pop().toInt())
        val depth = engine.pop() as Int
        println("${++ctr}:${"  ".repeat(depth)}$clazz.$method:$line")
        return null
    }
}