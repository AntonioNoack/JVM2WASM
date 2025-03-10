package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop
import java.nio.ByteBuffer

class LoadInstr(name: String, val impl: (ByteBuffer) -> Number) : SimpleInstr(name) {
    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val addr = stack.pop()!! as Int
        val data = engine.buffer
        data.position(addr)
        stack.add(impl(data))
        return null
    }
}