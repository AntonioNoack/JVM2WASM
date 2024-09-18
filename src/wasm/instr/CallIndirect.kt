package wasm.instr

class CallIndirect(val type: String) : Instruction {
    override fun toString(): String = "call_indirect $type"
}