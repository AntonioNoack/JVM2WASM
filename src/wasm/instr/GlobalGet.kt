package wasm.instr

import interpreter.WASMEngine

data class GlobalGet(val name: String) : Instruction {

    init {
        if (name.startsWith('$')) throw IllegalArgumentException(name)
    }

    override fun toString(): String = "global.get \$$name"
    override fun execute(engine: WASMEngine): String? {
        engine.stack.add(
            engine.globals[name]
                ?: throw IllegalStateException("Missing global $name")
        )
        return null
    }
}