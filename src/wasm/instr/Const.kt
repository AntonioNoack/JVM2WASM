package wasm.instr

import interpreter.WASMEngine
import jvm.JVMFlags.is32Bits
import me.anno.utils.structures.lists.LazyList
import utils.WASMType

data class Const(val type: ConstType, val value: Number) : Instruction {

    override fun toString(): String = "${type.wasmName}.const $value"

    override fun execute(engine: WASMEngine): String? {
        engine.push(value)
        return null
    }

    companion object {

        private val i32ConstCache = LazyList(512) { value ->
            Const(ConstType.I32, value)
        }

        fun ptrConst(value: Int): Const {
            return if (is32Bits) i32Const(value) else i64Const(value.toLong())
        }

        fun i32Const(value: Int): Const = i32ConstCache.getOrNull(value) ?: Const(ConstType.I32, value)
        fun i64Const(value: Long): Const = Const(ConstType.I64, value)
        fun f32Const(value: Float): Const = Const(ConstType.F32, value)
        fun f64Const(value: Double): Const = Const(ConstType.F64, value)

        val i32Const0 = i32Const(0)
        val i32Const1 = i32Const(1)
        val i32ConstM1 = i32Const(-1)
        val i32Const2 = i32Const(2)
        val i32Const3 = i32Const(3)
        val i32Const4 = i32Const(4)
        val i32Const5 = i32Const(5)
        val i64Const0 = i64Const(0)
        val i64Const1 = i64Const(1)
        val f32Const0 = f32Const(0f)
        val f32Const1 = f32Const(1f)
        val f32Const2 = f32Const(2f)
        val f64Const0 = f64Const(0.0)
        val f64Const1 = f64Const(1.0)

        val zero = mapOf(
            WASMType.I32 to i32Const0,
            WASMType.I64 to i64Const0,
            WASMType.F32 to f32Const0,
            WASMType.F64 to f64Const0
        )
    }
}