package wasm2cpp.expr

data class VariableExpr(
    val name: String,
    override val type: String
) : Expr