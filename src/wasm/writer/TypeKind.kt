package wasm.writer

enum class TypeKind(val id: Int) {
    I32(-1),
    I64(-2),
    F32(-3),
    F64(-4),

    V128(-5),
    I8(-6),
    I16(-7),
    EXN_REF(-0x17),
    FUNC_REF(-0x10),
    EXTERN_REF(-0x11),
    REFERENCE(-0x15),
    FUNC(-0x20),
    STRUCT(-0x21),
    ARRAY(-0x22),
    VOID(-0x40),

    ;

    val wasmName = name.lowercase()

    override fun toString(): String {
        return wasmName
    }

    companion object {
        val i32 = I32
        val i64 = I64
        val f32 = F32
        val f64 = F64

        fun find(name: String): TypeKind {
            return when (name) {
                "i32" -> I32
                "i64" -> I64
                "f32" -> F32
                "f64" -> F64
                else -> throw NotImplementedError()
            }
        }
    }
}