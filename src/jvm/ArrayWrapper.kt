package jvm

class ArrayWrapper<V>(val data: Array<V>, val offset: Int, override val size: Int) : List<V> {

    override fun get(index: Int): V = data[index + offset]
    override fun isEmpty(): Boolean = size <= 0
    override fun iterator(): Iterator<V> = listIterator()
    override fun listIterator(): ListIterator<V> = listIterator(0)
    override fun listIterator(index: Int): ListIterator<V> {
        return ArrayIterator(data, offset + index, size - index)
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<V> {
        return ArrayWrapper(data, offset + fromIndex, toIndex - fromIndex)
    }

    override fun lastIndexOf(element: V): Int {
        for (i in size - 1 downTo 0) {
            if (data[i + offset] == element) {
                return i
            }
        }
        return -1
    }

    override fun indexOf(element: V): Int {
        for (i in 0 until size) {
            if (data[i + offset] == element) {
                return i
            }
        }
        return -1
    }

    override fun containsAll(elements: Collection<V>): Boolean {
        return elements.all { contains(it) }
    }

    override fun contains(element: V): Boolean {
        return indexOf(element) >= 0
    }
}