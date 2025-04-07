package utils

import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.arrays.ByteArrayList

class StringBuilder2(initCapacity: Int = 1024) : ByteArrayList(initCapacity) {

    companion object {
        private val droppable = listOf("i32.const", "local.get")
    }

    constructor(base: CharSequence) : this(base.length) {
        append(base)
    }

    override fun toString(): String {
        return toString(0, size)
    }

    fun toString(i0: Int, i1: Int): String {
        return String(values, i0, i1 - i0)
    }

    fun append(str: CharSequence): StringBuilder2 {
        for (i in str.indices) {
            val c = str[i]
            if (c.code > 127) throw IllegalArgumentException("Character must be ascii: '$c'")
            add(c.code.toByte())
        }
        return this
    }

    fun drop() {
        // if endsWith number and \n, and before that i32.const or local.get,
        //  then remove that instead of dropping
        var i = trimSpacesAtEnd(size - 1)
        i = endsWithNumber(i)
        if (i >= 0) {
            if (this[i].toInt() == ' '.code) {
                for (drop in droppable) {
                    if (endsWith(drop, i)) {
                        size = i - drop.length
                        // if there is only spaces, remove until \n
                        while (size > 0 && this[size - 1].toInt() == ' '.code) {
                            size--
                        }
                        return // done :)
                    }
                }
            }
        }
        append("drop ")
    }

    fun trimSpacesAtEnd(i0: Int): Int {
        var i = i0
        while (i >= 0 && this[i].toInt().toChar() in " \n") {
            i--
        }
        return i
    }

    fun endsWithNumber(i0: Int): Int {
        var i = i0
        if (this[i] in '0'.code..'9'.code) {
            while (i >= 0 && this[i] in '0'.code..'9'.code) {
                i--
            }
            return i
        }
        return -1
    }

    fun append(c: Char): StringBuilder2 {
        add(c.code.toByte())
        return this
    }

    fun append(str: StringBuilder2): StringBuilder2 {
        return append(str, 0, str.length)
    }

    fun append(str: StringBuilder2, s0: Int): StringBuilder2 {
        return append(str, s0, str.length)
    }

    fun append(str: StringBuilder2, s0: Int, s1: Int): StringBuilder2 {
        assertTrue(s1 <= size || this !== str)
        addAll(str, s0, s1 - s0)
        return this
    }

    fun append(any: Any): StringBuilder2 {
        return append(any.toString())
    }

    fun remove(i0: Int, i1: Int): StringBuilder2 {
        assertTrue(i0 <= i1)
        assertTrue(i0 >= 0)
        assertTrue(i1 <= size)
        System.arraycopy(values, i1, values, i0, size - i1)
        size -= i1 - i0
        return this
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

    override fun hashCode(): Int {
        var hash = 0
        val values = values
        for (i in 0 until size) {
            hash = 31 * hash + values[i].toInt().and(255)
        }
        return hash
    }

    val length get() = size
}
