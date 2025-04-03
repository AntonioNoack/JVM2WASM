package wasm2cpp.instr

import wasm2cpp.StackElement

class Assignment(val name: String, val newValue: StackElement): CppInstruction {
    val type: String get() = newValue.type
}