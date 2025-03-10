package interpreter.functions

import interpreter.WASMEngine
import interpreter.WASMEngine.Companion.RETURN_LABEL
import wasm.instr.Instruction
import kotlin.math.min

object FillDoubleToString : Instruction {
    override fun execute(engine: WASMEngine): String {
        val dst = engine.getParam(0) as Int
        val value = engine.getParam(1) as Double
        val asString = value.toString()
        val data = engine.buffer
        data.position(dst + 4) // read length and skip to first write index
        val dstLength = data.getInt()
        val maxLength = min(asString.length, dstLength)
        for (i in 0 until maxLength) {
            data.putShort(asString[i].code.toShort())
        }
        engine.push(maxLength)
        return RETURN_LABEL
    }
}