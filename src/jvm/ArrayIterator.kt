package jvm

class ArrayIterator<V>(val data: Array<V>, val start: Int, var size: Int) : ListIterator<V> {
    private var index = start
    override fun hasNext(): Boolean = index < size
    override fun hasPrevious(): Boolean = index > start
    override fun next(): V = data[index++]
    override fun nextIndex(): Int = index
    override fun previous(): V = data[--index]
    override fun previousIndex(): Int = index - 1
}