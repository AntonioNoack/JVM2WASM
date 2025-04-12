package wasm2cpp.expr

data class CallExpr(
    val funcName: String, val params: List<Expr>,
    override val type: String
) : Expr