package interpreter.functions

import interpreter.WASMEngine
import wasm.instr.Instruction

object PrintStackTraceLine : Instruction {
    private var ctr = 0
    override fun execute(engine: WASMEngine): String? {
        // depth, class, method, line
        val line = engine.pop() as Int
        val method = engine.readString(engine.pop())
        val clazz = engine.readString(engine.pop())
        val depth = engine.pop() as Int
        println("${++ctr}:${"  ".repeat(depth)}$clazz.$method:$line")
        return null
    }
}