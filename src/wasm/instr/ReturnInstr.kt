package wasm.instr

import interpreter.WASMEngine

open class ReturnInstr(name: String) : SimpleInstr(name) {
    override fun isReturning(): Boolean = true
    override fun execute(engine: WASMEngine) = name
}