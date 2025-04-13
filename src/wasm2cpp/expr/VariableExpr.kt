package wasm2cpp.expr

data class VariableExpr(
    val name: String,
    override val jvmType: String
) : Expr