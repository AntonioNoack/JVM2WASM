package wasm.instr

enum class ConstType(val wasmType: String) {
    I32("i32"), I64("i64"),
    F32("f32"), F64("f64");

    companion object {
        fun find(name1: String): ConstType {
            return when (name1) {
                "i32" -> I32
                "i64" -> I64
                "f32" -> F32
                "f64" -> F64
                else -> throw IllegalArgumentException(name1)
            }
        }
    }
}