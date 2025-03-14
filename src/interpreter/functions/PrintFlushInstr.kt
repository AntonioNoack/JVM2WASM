package interpreter.functions

import interpreter.WASMEngine
import interpreter.functions.PrintByteInstr.err
import interpreter.functions.PrintByteInstr.log
import wasm.instr.Instruction

object PrintFlushInstr : Instruction {
    override fun execute(engine: WASMEngine): String? {
        val errStream = engine.pop().toInt() == 0
        if (errStream) {
            System.err.println(err)
            err.clear()
        } else {
            println(log)
            log.clear()
        }
        return null
    }
}