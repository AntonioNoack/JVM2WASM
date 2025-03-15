package wasm.instr

import interpreter.WASMEngine

object Drop  : SimpleInstr("drop") {
    override fun execute(engine: WASMEngine): String? {
        engine.pop()
        return null
    }
}