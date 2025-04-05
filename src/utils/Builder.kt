package utils

import wasm.instr.*
import wasm.instr.Instructions.Return
import wasm.instr.Instructions.Unreachable

// typealias Builder = StringBuilder2
class Builder(val instrs: ArrayList<Instruction>) {

    companion object {
        fun isDuplicable(lastInstr: Instruction?): Boolean {
            return lastInstr is LocalGet || lastInstr is ParamGet || lastInstr is GlobalGet || lastInstr is Const
        }

        fun canBeDropped(instr: Instruction?): Boolean {
            return when (instr) {
                is Const, is UnaryInstruction, is BinaryInstruction,
                is LocalGet, is ParamGet, is GlobalGet -> true
                else -> false
            }
        }
    }

    constructor() : this(ArrayList())
    constructor(capacity: Int) : this(ArrayList(capacity))
    constructor(instr: Instruction) : this(1) {
        append(instr)
    }

    val length get() = instrs.size

    fun append(instr: Instruction): Builder {
        val prevInstr = instrs.lastOrNull()
        if (prevInstr != Unreachable && prevInstr != Return) {
            instrs.add(instr)
        }
        return this
    }

    fun append(builder: List<Instruction>): Builder {
        (instrs as? ArrayList)?.ensureCapacity(instrs.size + builder.size)
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
        val offset = instrs.size - end.size
        if (offset < 0) return false
        for (i in end.indices) {
            if (end[i] != instrs[i + offset]) return false
        }
        return true
    }

    fun startsWith(instr: Instruction, i: Int): Boolean {
        return this.instrs.getOrNull(i) == instr
    }

    fun drop(): Builder {
        when (val last = lastOrNull()) {
            Return, Unreachable -> return this
            is Const, is LocalGet, is ParamGet, is GlobalGet -> {
                removeLast()
            }
            is Call -> {
                if (last.name.startsWith("getNth_")) removeLast()
                else append(Drop)
            }
            is UnaryInstruction -> {
                removeLast()
                drop()
            }
            is BinaryInstruction -> {
                removeLast()
                drop()
                drop()
            }
            else -> append(Drop)
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

    override fun toString(): String {
        return instrs.joinToString("\n")
    }
}