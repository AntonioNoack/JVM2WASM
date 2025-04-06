package highlevel

import interpreter.WASMEngine
import wasm.instr.Instruction

/**
 * just for testing things
 * */
class PseudoHighLevelInstr(
    val body: List<Instruction>
) : HighLevelInstruction() {

    override fun execute(engine: WASMEngine): String? {
        for (i in body.indices) {
            val instr = body[i]
            val result = instr.execute(engine)
            if (result != null) return result
        }
        return null
    }

    override fun toLowLevel(): List<Instruction> {
        return body
    }
}