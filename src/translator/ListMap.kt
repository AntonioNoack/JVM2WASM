package translator

class ListMap<V> : ArrayList<V?>() {
    override fun get(index: Int): V? {
        return if (index in indices) super.get(index) else null
    }

    override fun set(index: Int, element: V?): V? {
        while (index >= size) add(null)
        return super.set(index, element)
    }
}