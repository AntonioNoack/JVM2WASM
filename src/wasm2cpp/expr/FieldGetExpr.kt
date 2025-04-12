package wasm2cpp.expr

import utils.FieldSig

data class FieldGetExpr(val field: FieldSig, val instance: Expr): Expr {
    override val type get() = field.descriptor
}