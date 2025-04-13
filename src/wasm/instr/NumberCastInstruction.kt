package wasm.instr

import interpreter.WASMEngine
import wasm.writer.Opcode

class NumberCastInstruction(
    name: String, popType: String, pushType: String,
    opcode: Opcode, val impl: (Number) -> Number
) : UnaryInstruction(name, popType, pushType, UnaryOperator.ANY_CAST, opcode) {

    override fun execute(engine: WASMEngine): String? {
        val stack = engine.stack
        stack[stack.lastIndex] = impl(stack[stack.lastIndex])
        return null
    }

    companion object {

        fun u32ToF32(i: Number): Number {
            return i.toInt().toUInt().toFloat()
        }

        fun u64ToF32(i: Number): Number {
            return i.toLong().toULong().toFloat()
        }

        fun u32ToF64(i: Number): Number {
            return i.toInt().toUInt().toDouble()
        }

        fun u64ToF64(i: Number): Number {
            return i.toLong().toULong().toDouble()
        }

        fun i32BitsToF32(i: Number): Number {
            return Float.fromBits(i.toInt())
        }

        fun i64BitsToF64(i: Number): Number {
            return Double.fromBits(i.toLong())
        }

        fun f32BitsToI32(i: Number): Number {
            return i.toFloat().toRawBits()
        }

        fun f64BitsToI64(i: Number): Number {
            return i.toDouble().toRawBits()
        }
    }

}