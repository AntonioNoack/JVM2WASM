package interpreter.functions

import interpreter.WASMEngine
import utils.StaticClassIndices.FIRST_ARRAY
import utils.StaticClassIndices.LAST_ARRAY
import wasm.instr.Instruction

object TrackCallocInstr : Instruction {
    val counters = HashMap<Int, Int>()
    val arraySize = HashMap<Int, Int>()
    override fun execute(engine: WASMEngine): String? {
        val arraySizeI = engine.pop() as Int
        val classId = engine.pop() as Int
        counters[classId] = (counters[classId] ?: 0) + 1
        if (classId in FIRST_ARRAY..LAST_ARRAY) {
            arraySize[classId] = (arraySize[classId] ?: 0) + arraySizeI
        }
        return null
    }
}