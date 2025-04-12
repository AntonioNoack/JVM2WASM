package wasm.writer

class Table(val elemType: Type, val elemLimits: Limits) {
    override fun toString(): String {
        return "Table { $elemType, $elemLimits }"
    }
}