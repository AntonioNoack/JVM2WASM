package wasm2cpp.language

import wasm2cpp.expr.Expr

interface TargetLanguage {
    fun appendExpr(expr: Expr)
    fun appendExprSafely(expr: Expr)
}