package interpreter.functions

import interpreter.WASMEngine
import wasm.instr.Instruction

class GetterInstr(val getter: (WASMEngine) -> Number) : Instruction {
    override fun execute(engine: WASMEngine): String? {
        engine.push(getter(engine))
        return null
    }
}