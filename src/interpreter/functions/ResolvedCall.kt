package interpreter.functions

import interpreter.WASMEngine
import wasm.instr.Instruction
import wasm.parser.FunctionImpl

class ResolvedCall(val function: FunctionImpl) : Instruction {
    override fun execute(engine: WASMEngine): String? {
        engine.executeFunction(function)
        return null
    }

    override fun toString(): String = "call \$${function.funcName}"

}