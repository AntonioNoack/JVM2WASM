package wasm.instr

class Const(val type: ConstType, val value: String) : Instruction {
    override fun toString(): String = "${type.name1}.const $value"

    companion object {
        fun i32Const(value: String): Const = Const(ConstType.I32, value)
        fun i64Const(value: String): Const = Const(ConstType.I64, value)
        fun f32Const(value: String): Const = Const(ConstType.F32, value)
        fun f64Const(value: String): Const = Const(ConstType.F64, value)
        fun i32Const(value: Int): Const = Const(ConstType.I32, value.toString())
        fun i64Const(value: Long): Const = Const(ConstType.I64, value.toString())
        fun f32Const(value: Float): Const = Const(ConstType.F32, value.toString())
        fun f64Const(value: Double): Const = Const(ConstType.F64, value.toString())

        val zero = mapOf(
            "i32" to i32Const(0),
            "i64" to i64Const(0),
            "f32" to f32Const(0f),
            "f64" to f64Const(0.0)
        )
    }
}