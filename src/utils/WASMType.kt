package utils

enum class WASMType(val wasmName: String) {
    I32("i32"),
    I64("i64"),
    F32("f32"),
    F64("f64");

    override fun toString(): String {
        return wasmName
    }

    companion object {
        fun find(name: String): WASMType {
            return when (name) {
                "i32" -> I32
                "i64" -> I64
                "f32" -> F32
                "f64" -> F64
                else -> throw IllegalArgumentException(name)
            }
        }
    }
}