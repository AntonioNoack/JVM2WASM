package interpreter.functions

import interpreter.WASMEngine
import org.apache.logging.log4j.LoggerImpl
import wasm.instr.Instruction

class LogInstruction(
    private val logger: LoggerImpl,
    private val msg: String
) : Instruction {
    override fun execute(engine: WASMEngine): String? {
        logger.warn(msg)
        return null
    }

    override fun toString(): String {
        return ";; log '$msg'"
    }
}