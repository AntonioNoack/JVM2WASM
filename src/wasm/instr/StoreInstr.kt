package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop
import utils.WASMType
import wasm.writer.Opcode
import wasm.writer.TypeKind
import java.nio.ByteBuffer

class StoreInstr(
    name: String, val wasmType: WASMType, val numBytes: Int,
    opcode: Opcode, val impl: (ByteBuffer, Number) -> Unit
) : SimpleInstr(name, opcode) {

    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        val value = stack.pop()!!
        val addr = stack.pop()!!.toInt()
        store(engine, addr, value)
        return null
    }

    fun store(engine: WASMEngine, addr: Int, value: Number) {
        val data = engine.buffer
        data.position(addr)
        impl(data, value)
    }
}