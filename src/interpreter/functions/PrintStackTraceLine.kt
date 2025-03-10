package interpreter.functions

import interpreter.WASMEngine
import interpreter.WASMEngine.Companion.RETURN_LABEL
import wasm.instr.Instruction

object PrintStackTraceLine : Instruction {
    var ctr = 0
    override fun execute(engine: WASMEngine): String {
        // depth, class, method, line
        val depth = engine.getParam(0) as Int
        val clazz = engine.str(engine.getParam(1).toInt())
        val method = engine.str(engine.getParam(2).toInt())
        val line = engine.getParam(3) as Int
        println("${++ctr}:${"  ".repeat(depth)}$clazz.$method:$line")
        return RETURN_LABEL
    }
}