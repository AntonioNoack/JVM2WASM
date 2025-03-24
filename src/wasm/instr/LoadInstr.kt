package wasm.instr

import interpreter.WASMEngine
import java.nio.ByteBuffer

class LoadInstr(name: String, val numBytes: Int, val impl: (ByteBuffer) -> Number) : SimpleInstr(name) {
    override fun execute(engine: WASMEngine): String? {
        val addr = engine.pop().toInt()
        val data = engine.buffer
        if (addr !in 0 until data.capacity()) {
            throw IllegalStateException("Segfault! Tried to $name at $addr, capacity: ${data.capacity()}")
        }
        data.position(addr)
        engine.push(impl(data))
        return null
    }
}