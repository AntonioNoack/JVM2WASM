package wasm.writer

class StructType(val fields: List<Field>) : Type(TypeKind.STRUCT)