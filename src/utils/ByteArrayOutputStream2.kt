package utils

import me.anno.utils.structures.arrays.ExpandingByteArray
import java.io.OutputStream

class ByteArrayOutputStream2(capacity: Int = 16) : OutputStream() {

    val data = ExpandingByteArray(capacity)

    var position: Int
        get() = data.size
        set(value) {
            data.size = value
        }

    override fun write(p0: Int) {
        data.add(p0.toByte())
    }

    fun size() = position

    fun toByteArray() = data.toByteArray()

}