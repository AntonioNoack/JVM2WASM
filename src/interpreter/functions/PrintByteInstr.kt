package interpreter.functions

import interpreter.WASMEngine
import wasm.instr.Instruction

object PrintByteInstr : Instruction {
    val log = StringBuilder()
    val err = StringBuilder()
    override fun execute(engine: WASMEngine): String? {
        val errStream = engine.pop().toInt() == 0
        val char = (engine.pop() as Int).and(255).toChar()
        val buffer = if (errStream) err else log
        buffer.append(char)
        return null
    }
}