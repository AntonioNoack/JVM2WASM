package wasm2cpp

data class StackElement(val type: String, val name: String) {
    override fun toString(): String {
        return "$type $name"
    }
}