package utils

import wasm.instr.Comment
import wasm.instr.Instruction
import wasm.parser.LocalVariable

// typealias Builder = StringBuilder2
class Builder(capacity: Int = 16) {

    val localVariables = ArrayList<LocalVariable>()

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

    fun endsWith(instr: List<Instruction>): Boolean {
        return this.instr.subList(this.instr.size - instr.size, this.instr.size) == instr
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