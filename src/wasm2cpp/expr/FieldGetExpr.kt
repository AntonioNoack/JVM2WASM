package wasm2cpp.expr

import utils.FieldSig

data class FieldGetExpr(val field: FieldSig, val instance: Expr?, val isResultField: Boolean) : Expr {
    override val jvmType get() = field.descriptor
}