package interpreter.functions

import interpreter.WASMEngine
import wasm.instr.Instruction

object TrackCallocInstr : Instruction {
    val counters = HashMap<Int, Int>()
    override fun execute(engine: WASMEngine): String? {
        val classId = engine.pop() as Int
        counters[classId] = (counters[classId] ?: 0) + 1
        return null
    }
}