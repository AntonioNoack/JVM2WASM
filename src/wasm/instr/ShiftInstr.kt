package wasm.instr

import interpreter.WASMEngine
import wasm.writer.Opcode

class ShiftInstr(name: String, type: String, operator: BinaryOperator, opcode: Opcode, val impl: (Number, Int) -> Number) :
    BinaryInstruction(name, type, type, operator, opcode) {

    val isRight get() = name[6] == 'r'
    val isUnsigned get() = name[8] == 'u'

    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val i1 = stack.removeLast().toInt()
        val i0 = stack.removeLast()
        stack.add(impl(i0, i1))
        return null
    }
}