package highlevel

import interpreter.WASMEngine
import me.anno.utils.assertions.assertTrue
import utils.StringBuilder2
import wasm.instr.Comment
import wasm.instr.Instruction

/**
 * Instruction, that can be split into smaller, more basic instructions.
 * Used to generate high-level code like JavaScript (in the future)
 * */
abstract class HighLevelInstruction : Instruction {

    abstract fun toLowLevel(): List<Instruction>

    override fun execute(engine: WASMEngine): String? {
        return engine.executeInstructions(toLowLevel())
    }

    override fun toString(depth: Int, builder: StringBuilder2) {
        val instructions = toLowLevel()
        for (i in instructions.indices) {
            if (i > 0) builder.append('\n')
            val instr = instructions[i]
            instr.toString(depth, builder)
            assertTrue(instr !is Comment)
        }
    }

    override fun toString(): String {
        val tmp = StringBuilder2()
        toString(0, tmp)
        return tmp.toString()
    }
}