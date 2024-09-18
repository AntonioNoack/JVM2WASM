package wasm.writer

class Global(val initExpr: List<Expr>, val type: Type, val mutable: Boolean)