package wasm2cpp.instr

import wasm2cpp.StackElement

class Declaration(val type: String, val name: String, val initialValue: StackElement) : CppInstruction