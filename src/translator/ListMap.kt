package translator

class ListMap<V> : ArrayList<V?>() {
    override fun set(index: Int, element: V?): V? {
        while (index <= size) add(null)
        return super.set(index, element)
    }
}