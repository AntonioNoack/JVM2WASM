package wasm.writer

class Limits(val isShared: Boolean, val is64Bit: Boolean, val initial: Long, val max: Long?) {
    override fun toString(): String {
        return "Limits { shared: $isShared, 64-bit: $is64Bit, initial: $initial, max: $max }"
    }
}