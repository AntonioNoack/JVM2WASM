package wasm.writer

interface Expr
class Func
class Binary(val opcode: Opcode) : Expr
class Unary(val opcode: Opcode) : Expr
class Ternary(val opcode: Opcode) : Expr
class Block(val declIndex: Int, val expr: List<Expr>) : Expr
object Return : Expr