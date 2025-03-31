package wasm.instr

import interpreter.WASMEngine
import wasm.writer.Opcode

object Drop  : SimpleInstr("drop", Opcode.DROP) {
    override fun execute(engine: WASMEngine): String? {
        engine.pop()
        return null
    }
}