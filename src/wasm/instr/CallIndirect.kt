package wasm.instr

class CallIndirect(val type: String) : Instruction {

    init {
        if (type.startsWith('$')) throw IllegalArgumentException(type)
    }

    override fun toString(): String = "call_indirect (type \$$type)"
}