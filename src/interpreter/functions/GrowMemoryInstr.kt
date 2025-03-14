package interpreter.functions

import interpreter.WASMEngine
import me.anno.utils.files.Files.formatFileSize
import wasm.instr.Instruction
import java.nio.ByteBuffer
import java.nio.ByteOrder

object GrowMemoryInstr : Instruction {
    override fun execute(engine: WASMEngine): String? {
        val numExtraPages = engine.pop().toInt()
        val numExtraBytes = numExtraPages shl 16
        val oldBytes = engine.bytes
        val oldSize = oldBytes.size
        val newSize = oldSize + numExtraBytes
        System.err.println(
            "Growing memory (${oldSize.formatFileSize()}) by " +
                    numExtraBytes.formatFileSize()
        )
        val newBytes = oldBytes.copyOf(newSize)
        engine.bytes = newBytes
        engine.buffer = ByteBuffer.wrap(newBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
        engine.push(1) // 1 = success, 0 = failure
        return null
    }
}