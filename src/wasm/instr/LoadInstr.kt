package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop
import java.nio.ByteBuffer

class LoadInstr(name: String, val impl: (ByteBuffer) -> Number) : SimpleInstr(name) {
    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val addr = stack.pop()!! as Int
        val data = engine.buffer
        if (addr !in 0 until data.capacity()) {
            throw IllegalStateException("Segfault! Tried to $name at $addr, capacity: ${data.capacity()}")
        }
        data.position(addr)
        stack.add(impl(data))
        return null
    }
}