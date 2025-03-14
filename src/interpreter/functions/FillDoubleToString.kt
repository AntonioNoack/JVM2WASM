package interpreter.functions

import interpreter.WASMEngine
import wasm.instr.Instruction
import kotlin.math.min

object FillDoubleToString : Instruction {
    override fun execute(engine: WASMEngine): String? {
        val value = engine.pop() as Double
        val dst = engine.pop() as Int
        fillString(engine, dst, value.toString())
        return null
    }

    fun fillString(engine: WASMEngine, dst: Int, asString: String) {
        val data = engine.buffer
        data.position(dst + 4) // read length and skip to first write index
        val dstLength = data.getInt()
        val maxLength = min(asString.length, dstLength)
        for (i in 0 until maxLength) {
            data.putShort(asString[i].code.toShort())
        }
        engine.push(maxLength)
    }
}