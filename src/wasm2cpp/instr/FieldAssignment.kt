package wasm2cpp.instr

import utils.FieldSig
import wasm2cpp.StackElement

class FieldAssignment(val field: FieldSig, val instance: StackElement?, val newValue: StackElement) : CppInstruction {
    val jvmType: String get() = newValue.jvmType
}