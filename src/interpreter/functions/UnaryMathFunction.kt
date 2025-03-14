package interpreter.functions

import interpreter.WASMEngine
import wasm.instr.Instruction

class UnaryMathFunction<V>(val impl: (V) -> Number) : Instruction {
    override fun execute(engine: WASMEngine): String? {
        val input = engine.pop() as V
        val output = impl(input)
        engine.push(output)
        return null
    }
}