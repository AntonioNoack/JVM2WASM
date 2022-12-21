package utils

import me.anno.utils.structures.arrays.ExpandingGenericArray

class CodeBuilder(initCapacity: Int) : ExpandingGenericArray<Any>(initCapacity) {

    constructor(base: CharSequence) : this(base.length) {
        append(base)
    }

    override fun toString(): String {
        return (0 until size).joinToString("") {
            // todo make this nice :)
            this[it].toString()
        }
    }

    fun append(str: CodeBuilder, s0: Int = 0): CodeBuilder {
        if (str === this) throw IllegalArgumentException()
        ensureExtra(str.length - s0)
        for (i in s0 until str.length) {
            append(str[i])
        }
        return this
    }

    fun prepend(str: CodeBuilder): CodeBuilder {
        if (str.length > 0) {
            ensureExtra(str.length)
            val array = array!!
            System.arraycopy(array, 0, array, str.length, size)
            System.arraycopy(str.array!!, 0, array, 0, str.length)
        }
        return this
    }

    fun prepend(str: List<Any>): CodeBuilder {
        if (str.isNotEmpty()) {
            ensureExtra(str.size)
            val array = array!!
            System.arraycopy(array, 0, array, str.size, size)
            for (i in str.indices) {
                array[i] = str[i]
            }
        }
        return this
    }

    fun prepend(str: Any): CodeBuilder {
        if (str is CodeBuilder) throw IllegalArgumentException()
        add(0, str)
        return this
    }

    fun append(any: Any): CodeBuilder {
        if (any is CodeBuilder) throw IllegalArgumentException()
        add(any)
        return this
    }

    fun split(sep: Char): List<String> {
        if (length > 10000) println("warn! splitting long string $length")
        return toString().split(sep)
    }

    fun startsWith(start: CodeBuilder, i0: Int = 0): Boolean {
        if (length < start.length + i0) return false
        for (i in 0 until start.length) {
            if (start[i] != this[i0 + i])
                return false
        }
        return true
    }

    fun startsWith(start: List<Any>, i0: Int = 0): Boolean {
        if (length < start.size + i0) return false
        val array = array!!
        for (i in start.indices) {
            if (start[i] != array[i0 + i])
                return false
        }
        return true
    }

    fun startsWith(start: Any, i0: Int = 0): Boolean {
        return i0 in 0 until size && array!![i0] == start
    }

    fun endsWith(end: List<Any>): Boolean {
        val i0 = length - end.size
        if (i0 < 0) return false
        val array = array!!
        for (i in end.indices) {
            if (end[i] != array[i0 + i])
                return false
        }
        return true
    }

    fun endsWith(end: Any): Boolean {
        return size > 0 && array!![array!!.size - 1] == end
    }

    fun contains(start: List<Any>, i0: Int = 0, i1: Int = length - start.size): Boolean {
        for (i in i0 until i1) {
            if (startsWith(start, i))
                return true
        }
        return false
    }

    fun contains(start: Any, i0: Int = 0, i1: Int = length - 1): Boolean {
        for (i in i0 until i1) {
            if (startsWith(start, i))
                return true
        }
        return false
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is List<*> -> length == other.size && startsWith(other as List<Any>, 0)
            is CodeBuilder -> length == other.length && startsWith(other, 0)
            else -> false
        }
    }

    override fun hashCode(): Int {
        return array.hashCode()
    }

    val length get() = size
}
