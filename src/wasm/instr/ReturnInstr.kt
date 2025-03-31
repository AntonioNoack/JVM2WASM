package wasm.instr

import interpreter.WASMEngine
import wasm.writer.Opcode

open class ReturnInstr(name: String, opcode: Opcode) : SimpleInstr(name, opcode) {
    override fun isReturning(): Boolean = true
    override fun execute(engine: WASMEngine) = name
}