package utils

import wasm.instr.BinaryInstruction
import wasm.instr.Comment
import wasm.instr.Const
import wasm.instr.Instruction
import wasm.instr.Instructions.Drop
import wasm.instr.Instructions.Return
import wasm.instr.Instructions.Unreachable

// typealias Builder = StringBuilder2
class Builder(capacity: Int = 16) {

    val instrs = ArrayList<Instruction>(capacity)
    val length get() = instrs.size

    fun append(instr: Instruction): Builder {
        val prevInstr = instrs.lastOrNull()
        if (prevInstr != Unreachable && prevInstr != Return) {
            instrs.add(instr)
        }
        return this
    }

    fun append(builder: List<Instruction>): Builder {
        instrs.ensureCapacity(instrs.size + builder.size)
        for (instr in builder) append(instr)
        return this
    }

    fun append(builder: Builder): Builder {
        return append(builder.instrs)
    }

    fun prepend(builder: Builder): Builder {
        instrs.addAll(0, builder.instrs)
        // todo ensure nothing comes after Unreachable/Return
        return this
    }

    fun comment(line: String): Builder {
        append(Comment(line))
        return this
    }

    fun prepend(values: List<Instruction>): Builder {
        instrs.addAll(0, values)
        return this
    }

    fun prepend(instr: Instruction): Builder {
        this.instrs.add(0, instr)
        return this
    }

    fun lastOrNull(): Instruction? {
        var i = instrs.lastIndex
        while (i >= 0 && instrs[i] is Comment) i--
        return instrs.getOrNull(i)
    }

    fun endsWith(instr: Instruction): Boolean {
        return lastOrNull() == instr
    }

    fun removeLast() {
        var i = instrs.lastIndex
        while (i >= 0 && instrs[i] is Comment) i--
        instrs.subList(i, instrs.size).clear()
    }

    fun endsWith(end: List<Instruction>): Boolean {
        return instrs.size >= end.size &&
                instrs.subList(instrs.size - end.size, instrs.size) == end
    }

    fun startsWith(instr: Instruction, i: Int): Boolean {
        return this.instrs.getOrNull(i) == instr
    }

    fun drop(): Builder {
        val last = lastOrNull()
        val numDrop = when (last) {
            is Const -> 1
            is BinaryInstruction -> 2
            Return, Unreachable -> return this
            else -> -1
        }
        if (numDrop >= 0) {
            for (i in 0 until numDrop) {
                removeLast()
            }
        } else {
            append(Drop)
        }
        return this
    }

    fun clear() {
        instrs.clear()
    }

    override fun equals(other: Any?): Boolean {
        return instrs == other
    }

    override fun hashCode(): Int {
        return instrs.hashCode()
    }
}