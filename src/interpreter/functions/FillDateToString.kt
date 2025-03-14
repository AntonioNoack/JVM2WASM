package interpreter.functions

import interpreter.WASMEngine
import interpreter.functions.FillDoubleToString.fillString
import wasm.instr.Instruction
import java.text.SimpleDateFormat
import java.util.*

object FillDateToString : Instruction {
    private val formatter = SimpleDateFormat("hh:mm:ss.sss")
    override fun execute(engine: WASMEngine): String? {
        val dst = engine.pop() as Int
        fillString(engine, dst, formatter.format(Date()))
        return null
    }
}