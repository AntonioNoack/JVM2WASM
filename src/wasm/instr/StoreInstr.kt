package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop
import java.nio.ByteBuffer

class StoreInstr(name: String, val numBytes: Int, val impl: (ByteBuffer, Number) -> Unit) : SimpleInstr(name) {
    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val value = stack.pop()!!
        val addr = stack.pop() as Int
        val data = engine.buffer
        data.position(addr)
        impl(data, value)
        return null
    }
}