package wasm.parser

import me.anno.utils.assertions.assertEquals
import me.anno.utils.structures.arrays.ByteArrayList
import me.anno.utils.structures.arrays.IntArrayList

class TokenList(val text: String) {

    private val types = ByteArrayList(256)
    private val starts = IntArrayList(256)
    private val ends = IntArrayList(256)

    val size get() = types.size

    fun push(type: TokenType, startIncl: Int, endExcl: Int) {
        types.add(type.ordinal.toByte())
        starts.add(startIncl)
        ends.add(endExcl)
    }

    fun getType(i: Int): TokenType {
        return TokenType.entries[types[i].toInt()]
    }

    fun getStart(i: Int): Int {
        return starts[i]
    }

    fun getEnd(i: Int): Int {
        return ends[i]
    }

    fun getString(i: Int): String {
        return when (getType(i)) {
            TokenType.OPEN_BRACKET -> "("
            TokenType.CLOSE_BRACKET -> ")"
            else -> text.substring(getStart(i), getEnd(i))
        }
    }

    private fun getToken(i: Int): String {
        return when (val type = getType(i)) {
            TokenType.OPEN_BRACKET -> "("
            TokenType.CLOSE_BRACKET -> ")"
            else -> text.substring(getStart(i) - type.prefix, getEnd(i))
        }
    }

    fun subList(startIncl: Int, endExcl: Int): List<String> {
        return (startIncl until endExcl).map { getToken(it) }
    }

    fun consume(type: TokenType, name: String, i: Int) {
        assertEquals(name, consume(type, i))
    }

    fun consume(type: TokenType, i: Int): String {
        assertEquals(type, getType(i))
        return getString(i)
    }

    override fun toString(): String {
        return subList(0, size).toString()
    }
}