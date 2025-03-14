package utils

import utils.WASMTypes.*

enum class CallType(val symbol: Char) {
    POINTER('T'),
    I32('I'), I64('J'),
    F32('F'), F64('D');

    fun toWASM(): String {
        return when (this) {
            POINTER -> ptrType
            I32 -> i32
            I64 -> i64
            F32 -> f32
            F64 -> f64
        }
    }
}