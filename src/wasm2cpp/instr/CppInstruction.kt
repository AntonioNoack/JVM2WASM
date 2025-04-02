package wasm2cpp.instr

import interpreter.WASMEngine
import wasm.instr.Instruction

/**
 * Declarative-style instructions, that are probably not WASM-compatible without extra work
 * */
interface CppInstruction: Instruction {
    override fun execute(engine: WASMEngine): String? {
        throw NotImplementedError()
    }
}