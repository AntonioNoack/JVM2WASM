package wasm2cpp.instr

import wasm2cpp.StackElement

class CppLoadInstr(val type: String, val newName: String, val memoryType: String, val addrExpr: StackElement) :
    CppInstruction