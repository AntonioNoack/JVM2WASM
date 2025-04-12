package wasm2cpp

import wasm2cpp.expr.Expr

data class StackElement(
    val expr: Expr,
    val dependencies: List<String>,
    val isBoolean: Boolean
) {
    val type get() = expr.type
}