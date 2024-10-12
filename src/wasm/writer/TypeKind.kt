package wasm.writer

enum class TypeKind(val id: Int) {
    I32(0x7f),
    I64(0x7e),
    F32(0x7d),
    F64(0x7c),
    V128(0x7b),
    I8(0x7a),
    I16(0x79),
    FUNC_REF(0x70),
    EXTERN_REF(0x6f),
    REFERENCE(0x6b),
    FUNC(0x60),
    STRUCT(0x5f),
    ARRAY(0x5e),
    VOID(0x40),

    ;

    companion object {
        val i32 = I32
        val i64 = I64
        val f32 = F32
        val f64 = F64
    }
}