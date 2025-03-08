package wasm2cpp

data class StackElement(
    val type: String,
    val expr: String,
    val names: List<String>,
    val isBoolean: Boolean
)