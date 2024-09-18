package wasm.writer

val x = TypeKind.VOID
val i32 = TypeKind.I32
val i64 = TypeKind.I64
val f32 = TypeKind.F32
val f64 = TypeKind.F64

// https://github.com/WebAssembly/wabt/blob/a4a77c18df16d6ee672f2a2564969bc9b2beef3a/include/wabt/opcode.def
enum class Opcode(
    val resultType: TypeKind,
    val first: TypeKind,
    val second: TypeKind,
    val third: TypeKind,
    val memorySize: Int,
    val prefix: Int,
    val opcode: Int,
) {
    UNREACHABLE(0),
    BLOCK(2),
    LOOP(3),
    IF(4),
    ELSE(5),
    END(0x0b),
    BR(0x0c),
    BR_IF(x, i32, x, 0x0d),
    BR_TABLE(x, i32, x, 0x0e),
    RETURN(0x0f),
    CALL(0x10),
    CALL_INDIRECT(0x11),
    RETURN_CALL(0x12),
    RETURN_CALL_INDIRECT(0x13),
    DROP(0x1a),
    ;

    constructor(resultType: TypeKind, first: TypeKind, second: TypeKind, opcode: Int) :
            this(resultType, first, second, x, 0, 0, opcode)

    constructor(opcode: Int) :
            this(x, x, x, x, 0, 0, opcode)
}

class Func
abstract class Expr
class Binary(val opcode: Opcode) : Expr()
class Unary(val opcode: Opcode) : Expr()
class Ternary(val opcode: Opcode) : Expr()
class Block(val declIndex: Int, val expr: List<Expr>) : Expr()
object Return : Expr()