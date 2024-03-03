package utils

import me.anno.utils.structures.arrays.ExpandingByteArray

class StringBuilder2(initCapacity: Int) : ExpandingByteArray(initCapacity) {

    constructor(base: CharSequence) : this(base.length) {
        append(base)
    }

    override fun toString(): String {
        val array = array ?: return ""
        return String(array, 0, size)
    }

    fun append(str: CharSequence): StringBuilder2 {
        ensureExtra(str.length)
        for (i in str.indices) {
            val c = str[i]
            if (c.code > 127) throw IllegalArgumentException("Character must be ascii: '$c'")
            add(c.code.toByte())
        }
        return this
    }

    fun append(c: Char): StringBuilder2 {
        add(c.code.toByte())
        return this
    }

    fun append(str: StringBuilder2, s0: Int = 0): StringBuilder2 {
        if (str === this) throw IllegalArgumentException()
        addAll(str, s0, str.length - s0)
        return this
    }

    fun prepend(str: StringBuilder2): StringBuilder2 {
        val data = str.array ?: return this
        return prepend(data, str.length)
    }

    fun prepend(str: String): StringBuilder2 {
        val data = str.toByteArray()
        return prepend(data, str.length)
    }

    private fun prepend(data: ByteArray, strLength: Int): StringBuilder2 {
        ensureExtra(strLength)
        val array = array!!
        System.arraycopy(array, 0, array, strLength, length) // move back
        System.arraycopy(data, 0, array, 0, strLength) // insert new data
        size += strLength
        return this
    }

    fun append(any: Any): StringBuilder2 {
        return append(any.toString())
    }

    fun split(sep: Char): List<String> {
        if (length > 10000) println("warn! splitting long string $length")
        return toString().split(sep)
    }

    fun startsWith(start: StringBuilder2, i0: Int = 0): Boolean {
        if (length < start.length + i0) return false
        for (i in 0 until start.length) {
            if (start[i] != this[i0 + i])
                return false
        }
        return true
    }

    fun startsWith(start: String, i0: Int = 0): Boolean {
        if (length < start.length + i0) return false
        for (i in start.indices) {
            if (start[i].code != this[i0 + i].toInt())
                return false
        }
        return true
    }

    fun endsWith(end: String, endIndex: Int = length): Boolean {
        val i0 = endIndex - end.length
        if (i0 < 0) return false
        for (i in end.indices) {
            if (end[i].code != this[i0 + i].toInt()) {
                return false
            }
        }
        return true
    }

    fun contains(start: String, i0: Int = 0, i1: Int = length - start.length): Boolean {
        for (i in i0 until i1) {
            if (startsWith(start, i)) {
                return true
            }
        }
        return false
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is String -> length == other.length && startsWith(other, 0)
            is StringBuilder2 -> length == other.length && startsWith(other, 0)
            else -> false
        }
    }

    val length get() = size
}
