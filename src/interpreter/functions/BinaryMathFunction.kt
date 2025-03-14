package interpreter.functions

import interpreter.WASMEngine
import wasm.instr.Instruction

class BinaryMathFunction(val impl: (Double, Double) -> Double) : Instruction {
    override fun execute(engine: WASMEngine): String? {
        val input2 = engine.pop() as Double
        val input1 = engine.pop() as Double
        val output = impl(input1, input2)
        engine.push(output)
        return null
    }
}