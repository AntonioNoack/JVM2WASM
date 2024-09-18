package wasm.instr

class Const(val type: String, val value: String) : Instruction {
    override fun toString(): String = "$type.const $value"
}