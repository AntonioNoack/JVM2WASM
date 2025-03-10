package interpreter.functions

import interpreter.WASMEngine
import interpreter.WASMEngine.Companion.RETURN_LABEL
import wasm.instr.Instruction

class NativeLogInstr(val types: String) : Instruction {
    val builder = StringBuilder()
    override fun execute(engine: WASMEngine): String {
        for (i in types.indices) {
            if (i > 0) builder.append(", ")
            val value = engine.getParam(i)
            if (types[i] == '?') {
                builder.append(engine.str(value.toInt()))
            } else {
                builder.append(value)
            }
        }
        println(builder)
        builder.clear()
        return RETURN_LABEL
    }
}