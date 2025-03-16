package interpreter.functions

import interpreter.WASMEngine
import utils.StaticClassIndices.FIRST_ARRAY
import utils.StaticClassIndices.LAST_ARRAY
import wasm.instr.Instruction

object TrackCallocInstr : Instruction {
    val counters = HashMap<Int, Int>(64)
    val arraySize = IntArray(LAST_ARRAY + 1)
    override fun execute(engine: WASMEngine): String? {
        val arraySizeI = engine.pop() as Int
        val classId = engine.pop() as Int
        counters[classId] = (counters[classId] ?: 0) + 1
        if (classId in FIRST_ARRAY..LAST_ARRAY) {
            arraySize[classId] += arraySizeI
        }
        return null
    }
}