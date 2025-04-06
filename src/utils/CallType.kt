package utils

enum class CallType(val symbol: Char) {
    POINTER('T'),
    I32('I'), I64('J'),
    F32('F'), F64('D');

    fun toWASM(): WASMType {
        return when (this) {
            POINTER -> ptrTypeI
            I32 -> WASMType.I32
            I64 -> WASMType.I64
            F32 -> WASMType.F32
            F64 -> WASMType.F64
        }
    }
}