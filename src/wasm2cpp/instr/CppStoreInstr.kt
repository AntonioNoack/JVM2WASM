package wasm2cpp.instr

import wasm2cpp.StackElement

class CppStoreInstr(val type: String, val memoryType: String, val addrExpr: StackElement, val valueExpr: StackElement) :
    CppInstruction