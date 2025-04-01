package wasm.instr

import interpreter.WASMEngine
import wasm.writer.Opcode
import java.nio.ByteBuffer

class LoadInstr(name: String, val numBytes: Int, opcode: Opcode, val impl: (ByteBuffer) -> Number) :
    SimpleInstr(name, opcode) {

    override fun execute(engine: WASMEngine): String? {
        val addr = engine.pop().toInt()
        load(engine, addr)
        return null
    }

    fun load(engine: WASMEngine, addr: Int) {
        val data = engine.buffer
        if (addr !in 0 until data.capacity()) {
            throw IllegalStateException("Segfault! Tried to $name at $addr, capacity: ${data.capacity()}")
        }
        data.position(addr)
        engine.push(impl(data))
    }
}