package wasm2cpp.expr

data class ConstExpr(
    val value: Any, // could be number, but also String for high-level languages
    override val jvmType: String
) : Expr