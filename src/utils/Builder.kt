package utils

import wasm.instr.Comment
import wasm.instr.Instruction
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

    fun endsWith(instr: Instruction): Boolean {
        var i = instrs.lastIndex
        while (i >= 0 && instrs[i] is Comment) i--
        return this.instrs.getOrNull(i) == instr
    }

    fun endsWith(end: List<Instruction>): Boolean {
        return instrs.size >= end.size &&
                instrs.subList(instrs.size - end.size, instrs.size) == end
    }

    fun startsWith(instr: Instruction, i: Int): Boolean {
        return this.instrs.getOrNull(i) == instr
    }

    fun drop(): Builder {
        instrs.removeLast()
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