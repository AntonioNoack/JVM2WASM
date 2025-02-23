package wasm.instr

open class ReturnInstr(name: String) : SimpleInstr(name) {
    override fun isReturning(): Boolean = true
}