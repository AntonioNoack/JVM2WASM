package wasm.writer

class FuncTypeI(val name: String, val paramTypes: List<Type>, val resultTypes: List<Type>) : Type(TypeKind.FUNC)