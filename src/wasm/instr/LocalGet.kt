package wasm.instr

import interpreter.WASMEngine

data class LocalGet(var name: String) : Instruction {

    init {
        if (name.startsWith('$')) throw IllegalArgumentException(name)
    }

    override fun toString(): String = "local.get \$$name"

    override fun execute(engine: WASMEngine): String? {
        val local = engine.stackFrames.last().locals
        engine.push(local[name] ?: throw IllegalStateException("Missing local $name"))
        return null
    }
}