package wasm.instr

import interpreter.WASMEngine

data class LocalSet(var name: String) : Instruction {

    init {
        if (name.startsWith('$')) throw IllegalArgumentException(name)
    }

    override fun toString(): String = "local.set \$$name"
    override fun execute(engine: WASMEngine): String? {
        val local = engine.stackFrames.last().locals
        local[name] = engine.pop()
        return null
    }
}