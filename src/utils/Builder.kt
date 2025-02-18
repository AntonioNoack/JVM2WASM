package utils

import wasm.instr.Comment
import wasm.instr.Instruction

// typealias Builder = StringBuilder2
class Builder(capacity: Int = 16) {

    val instr = ArrayList<Instruction>(capacity)
    val length get() = instr.size

    fun append(instr: Instruction): Builder {
        this.instr.add(instr)
        return this
    }

    @Deprecated("Please use instruction objects")
    fun append(instr: String): Builder {
        TODO()
        return this
    }

    fun append(builder: Builder): Builder {
        instr.addAll(builder.instr)
        return this
    }

    fun prepend(builder: Builder): Builder {
        instr.addAll(0, builder.instr)
        return this
    }

    fun comment(line: String): Builder {
        append(Comment(line))
        return this
    }

    fun prepend(values: List<Instruction>): Builder {
        instr.addAll(0, values)
        return this
    }

    fun endsWith(instr: Instruction): Boolean {
        return this.instr.lastOrNull() == instr
    }

    fun endsWith(end: List<Instruction>): Boolean {
        return instr.size >= end.size &&
                instr.subList(instr.size - end.size, instr.size) == end
    }

    fun startsWith(instr: Instruction, i: Int): Boolean {
        return this.instr.getOrNull(i) == instr
    }

    fun drop(): Builder {
        instr.removeLast()
        return this
    }

    fun clear() {
        instr.clear()
    }
}