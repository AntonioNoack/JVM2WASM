package wasm.instr

import utils.WASMType
import utils.WASMTypes.*
import wasm.instr.NumberCastInstruction.Companion.f32BitsToI32
import wasm.instr.NumberCastInstruction.Companion.f64BitsToI64
import wasm.instr.NumberCastInstruction.Companion.i32BitsToF32
import wasm.instr.NumberCastInstruction.Companion.i64BitsToF64
import wasm.instr.NumberCastInstruction.Companion.u32ToF32
import wasm.instr.NumberCastInstruction.Companion.u32ToF64
import wasm.instr.NumberCastInstruction.Companion.u64ToF32
import wasm.instr.NumberCastInstruction.Companion.u64ToF64
import wasm.writer.Opcode
import kotlin.math.*

object Instructions {

    val I32Add = BinaryI32Instruction("i32.add", BinaryOperator.ADD, Opcode.I32_ADD, Int::plus)
    val I32Sub = BinaryI32Instruction("i32.sub", BinaryOperator.SUB, Opcode.I32_SUB, Int::minus)
    val I32Mul = BinaryI32Instruction("i32.mul", BinaryOperator.MULTIPLY, Opcode.I32_MUL, Int::times)
    val I32_DIVS = BinaryI32Instruction("i32.div_s", BinaryOperator.DIVIDE, Opcode.I32_DIVS, Int::div)

    val I64Add = BinaryI64Instruction("i64.add", BinaryOperator.ADD, Opcode.I64_ADD, Long::plus)
    val I64Sub = BinaryI64Instruction("i64.sub", BinaryOperator.SUB, Opcode.I64_SUB, Long::minus)
    val I64Mul = BinaryI64Instruction("i64.mul", BinaryOperator.MULTIPLY, Opcode.I64_MUL, Long::times)
    val I64_DIVS = BinaryI64Instruction("i64.div_s", BinaryOperator.DIVIDE, Opcode.I64_DIVS, Long::div)

    val F32Add = BinaryF32Instruction("f32.add", BinaryOperator.ADD, Opcode.F32_ADD, Float::plus)
    val F32Sub = BinaryF32Instruction("f32.sub", BinaryOperator.SUB, Opcode.F32_SUB, Float::minus)
    val F32Mul = BinaryF32Instruction("f32.mul", BinaryOperator.MULTIPLY, Opcode.F32_MUL, Float::times)
    val F32Div = BinaryF32Instruction("f32.div", BinaryOperator.DIVIDE, Opcode.F32_DIV, Float::div)

    val F64Add = BinaryF64Instruction("f64.add", BinaryOperator.ADD, Opcode.F64_ADD, Double::plus)
    val F64Sub = BinaryF64Instruction("f64.sub", BinaryOperator.SUB, Opcode.F64_SUB, Double::minus)
    val F64Mul = BinaryF64Instruction("f64.mul", BinaryOperator.MULTIPLY, Opcode.F64_MUL, Double::times)
    val F64Div = BinaryF64Instruction("f64.div", BinaryOperator.DIVIDE, Opcode.F64_DIV, Double::div)

    val I32Store8 = StoreInstr("i32.store8", WASMType.I32, 1, Opcode.I32_STORE8) { m, v -> m.put(v.toByte()) }
    val I32Store16 = StoreInstr("i32.store16", WASMType.I32, 2, Opcode.I32_STORE16) { m, v -> m.putShort(v.toShort()) }
    val I32Store = StoreInstr("i32.store", WASMType.I32, 4, Opcode.I32_STORE) { m, v -> m.putInt(v.toInt()) }
    val I64Store = StoreInstr("i64.store", WASMType.I64, 8, Opcode.I64_STORE) { m, v -> m.putLong(v.toLong()) }
    val F32Store = StoreInstr("f32.store", WASMType.F32, 4, Opcode.F32_STORE) { m, v -> m.putFloat(v.toFloat()) }
    val F64Store = StoreInstr("f64.store", WASMType.F64, 8, Opcode.F64_STORE) { m, v -> m.putDouble(v.toDouble()) }

    val I32Load8U = LoadInstr("i32.load8_u", WASMType.I32, 1, Opcode.I32_LOAD8U) { it.get().toInt() }
    val I32Load8S = LoadInstr("i32.load8_s", WASMType.I32, 1, Opcode.I32_LOAD8S) { it.get().toInt().and(0xff) }
    val I32Load16U =
        LoadInstr("i32.load16_u", WASMType.I32, 2, Opcode.I32_LOAD16U) { it.getShort().toInt().and(0xffff) }
    val I32Load16S = LoadInstr("i32.load16_s", WASMType.I32, 2, Opcode.I32_LOAD16S) { it.getShort() }
    val I32Load = LoadInstr("i32.load", WASMType.I32, 4, Opcode.I32_LOAD) { it.getInt() }
    val I64Load = LoadInstr("i64.load", WASMType.I64, 8, Opcode.I64_LOAD) { it.getLong() }
    val F32Load = LoadInstr("f32.load", WASMType.F32, 4, Opcode.F32_LOAD) { it.getFloat() }
    val F64Load = LoadInstr("f64.load", WASMType.F64, 8, Opcode.F64_LOAD) { it.getDouble() }

    val F32Trunc = UnaryFloatInstruction("f32.trunc", f32, UnaryOperator.TRUNCATE, Opcode.F32_TRUNC) { truncate(it) }
    val F64Trunc = UnaryFloatInstruction("f64.trunc", f64, UnaryOperator.TRUNCATE, Opcode.F64_TRUNC) { truncate(it) }

    val F32GE = Compare0Instr("f32.ge", BinaryOperator.GREATER_EQUAL, f32, Opcode.F32_GE)
    val F32GT = Compare0Instr("f32.gt", BinaryOperator.GREATER, f32, Opcode.F32_GT)
    val F32LE = Compare0Instr("f32.le", BinaryOperator.LESS_EQUAL, f32, Opcode.F32_LE)
    val F32LT = Compare0Instr("f32.lt", BinaryOperator.LESS, f32, Opcode.F32_LT)
    val F32NE = Compare0Instr("f32.ne", BinaryOperator.NOT_EQUAL, f32, Opcode.F32_NE)
    val F32EQ = Compare0Instr("f32.eq", BinaryOperator.EQUAL, f32, Opcode.F32_EQ)

    val F64GE = Compare0Instr("f64.ge", BinaryOperator.GREATER_EQUAL, f64, Opcode.F64_GE)
    val F64GT = Compare0Instr("f64.gt", BinaryOperator.GREATER, f64, Opcode.F64_GT)
    val F64LE = Compare0Instr("f64.le", BinaryOperator.LESS_EQUAL, f64, Opcode.F64_LE)
    val F64LT = Compare0Instr("f64.lt", BinaryOperator.LESS, f64, Opcode.F64_LT)
    val F64NE = Compare0Instr("f64.ne", BinaryOperator.NOT_EQUAL, f64, Opcode.F64_NE)
    val F64EQ = Compare0Instr("f64.eq", BinaryOperator.EQUAL, f64, Opcode.F64_EQ)

    val I32_TRUNC_F32S = NumberCastInstruction("i32.trunc_f32_s", f32, i32, Opcode.I32_TRUNC_F32S, Number::toInt)
    val I32_TRUNC_F64S = NumberCastInstruction("i32.trunc_f64_s", f64, i32, Opcode.I32_TRUNC_F64S, Number::toInt)
    val I64_TRUNC_F32S = NumberCastInstruction("i64.trunc_f32_s", f32, i64, Opcode.I64_TRUNC_F32S, Number::toLong)
    val I64_TRUNC_F64S = NumberCastInstruction("i64.trunc_f64_s", f64, i64, Opcode.I64_TRUNC_F64S, Number::toLong)

    val F64_PROMOTE_F32 = NumberCastInstruction("f64.promote_f32", f32, f64, Opcode.F64_PROMOTE_F32, Number::toDouble)
    val F32_DEMOTE_F64 = NumberCastInstruction("f32.demote_f64", f64, f32, Opcode.F32_DEMOTE_F64, Number::toFloat)

    val I64_EXTEND_I32S = NumberCastInstruction("i64.extend_i32_s", i32, i64, Opcode.I64_EXTEND_I32S, Number::toLong)
    val I64_EXTEND_I32U = NumberCastInstruction("i64.extend_i32_u", i32, i64, Opcode.I64_EXTEND_I32U, Number::toLong)
    val I32_WRAP_I64 = NumberCastInstruction("i32.wrap_i64", i64, i32, Opcode.I32_WRAP_I64, Number::toInt)
    val F32_CONVERT_I32S =
        NumberCastInstruction("f32.convert_i32_s", i32, f32, Opcode.F32_CONVERT_I32S, Number::toFloat)
    val F32_CONVERT_I64S =
        NumberCastInstruction("f32.convert_i64_s", i64, f32, Opcode.F32_CONVERT_I64S, Number::toFloat)
    val F64_CONVERT_I32S =
        NumberCastInstruction("f64.convert_i32_s", i32, f64, Opcode.F64_CONVERT_I32S, Number::toDouble)
    val F64_CONVERT_I64S =
        NumberCastInstruction("f64.convert_i64_s", i64, f64, Opcode.F64_CONVERT_I64S, Number::toDouble)

    val F32_CONVERT_I32U = NumberCastInstruction("f32.convert_i32_u", i32, f64, Opcode.F32_CONVERT_I32U, ::u32ToF32)
    val F32_CONVERT_I64U = NumberCastInstruction("f32.convert_i64_u", i64, f64, Opcode.F32_CONVERT_I64U, ::u64ToF32)
    val F64_CONVERT_I32U = NumberCastInstruction("f64.convert_i32_u", i32, f64, Opcode.F64_CONVERT_I32U, ::u32ToF64)
    val F64_CONVERT_I64U = NumberCastInstruction("f64.convert_i64_u", i64, f64, Opcode.F64_CONVERT_I64U, ::u64ToF64)

    val I32_REINTERPRET_F32 =
        NumberCastInstruction("i32.reinterpret_f32", f32, i32, Opcode.I32_REINTERPRET_F32, ::f32BitsToI32)
    val F32_REINTERPRET_I32 =
        NumberCastInstruction("f32.reinterpret_i32", i32, f32, Opcode.F32_REINTERPRET_I32, ::i32BitsToF32)
    val I64_REINTERPRET_F64 =
        NumberCastInstruction("i64.reinterpret_f64", f64, i64, Opcode.I64_REINTERPRET_F64, ::f64BitsToI64)
    val F64_REINTERPRET_I64 =
        NumberCastInstruction("f64.reinterpret_i64", i64, f64, Opcode.F64_REINTERPRET_I64, ::i64BitsToF64)

    val I32GES = Compare0Instr("i32.ge_s", BinaryOperator.GREATER_EQUAL, i32, Opcode.I32_GES)
    val I32GTS = Compare0Instr("i32.gt_s", BinaryOperator.GREATER, i32, Opcode.I32_GTS)
    val I32LES = Compare0Instr("i32.le_s", BinaryOperator.LESS_EQUAL, i32, Opcode.I32_LES)
    val I32LTS = Compare0Instr("i32.lt_s", BinaryOperator.LESS, i32, Opcode.I32_LTS)

    val I32GEU = CompareU32Instr("i32.ge_u", BinaryOperator.GREATER_EQUAL, Opcode.I32_GEU)
    val I32GTU = CompareU32Instr("i32.gt_u", BinaryOperator.GREATER, Opcode.I32_GTU)
    val I32LEU = CompareU32Instr("i32.le_u", BinaryOperator.LESS_EQUAL, Opcode.I32_LEU)
    val I32LTU = CompareU32Instr("i32.lt_u", BinaryOperator.LESS, Opcode.I32_LTU)

    val I64GES = Compare0Instr("i64.ge_s", BinaryOperator.GREATER_EQUAL, i64, Opcode.I64_GES)
    val I64GTS = Compare0Instr("i64.gt_s", BinaryOperator.GREATER, i64, Opcode.I64_GTS)
    val I64LES = Compare0Instr("i64.le_s", BinaryOperator.LESS_EQUAL, i64, Opcode.I64_LES)
    val I64LTS = Compare0Instr("i64.lt_s", BinaryOperator.LESS, i64, Opcode.I64_LTS)

    val I64GEU = CompareU64Instr("i64.ge_u", BinaryOperator.GREATER_EQUAL, Opcode.I64_GEU)
    val I64GTU = CompareU64Instr("i64.gt_u", BinaryOperator.GREATER, Opcode.I64_GTU)
    val I64LEU = CompareU64Instr("i64.le_u", BinaryOperator.LESS_EQUAL, Opcode.I64_LEU)
    val I64LTU = CompareU64Instr("i64.lt_u", BinaryOperator.LESS, Opcode.I64_LTU)

    val I32EQZ = EqualsZeroInstruction("i32.eqz", i32, Opcode.I32_EQZ)
    val I64EQZ = EqualsZeroInstruction("i64.eqz", i64, Opcode.I64_EQZ)

    val I32EQ = Compare0Instr("i32.eq", BinaryOperator.EQUAL, i32, Opcode.I32_EQ)
    val I32NE = Compare0Instr("i32.ne", BinaryOperator.NOT_EQUAL, i32, Opcode.I32_NE)
    val I64EQ = Compare0Instr("i64.eq", BinaryOperator.EQUAL, i64, Opcode.I64_EQ)
    val I64NE = Compare0Instr("i64.ne", BinaryOperator.NOT_EQUAL, i64, Opcode.I64_NE)

    // todo is this the same for negative numbers in C++, Java and WASM?
    val I32_REM_S = BinaryI32Instruction("i32.rem_s", BinaryOperator.REMAINDER, Opcode.I32_REMS) { a, b -> a % b }
    val I64_REM_S = BinaryI64Instruction("i64.rem_s", BinaryOperator.REMAINDER, Opcode.I64_REMS) { a, b -> a % b }

    val Return = ReturnInstr("return", Opcode.RETURN)
    val Unreachable = ReturnInstr("unreachable", Opcode.UNREACHABLE)

    val I32Shl = ShiftInstr("i32.shl", i32, BinaryOperator.SHIFT_LEFT, Opcode.I32_SHL) { a, b -> a.toInt().shl(b) }
    val I32ShrU =
        ShiftInstr("i32.shr_u", i32, BinaryOperator.SHIFT_RIGHT_UNSIGNED, Opcode.I32_SHRU) { a, b -> a.toInt().ushr(b) }
    val I32ShrS =
        ShiftInstr("i32.shr_s", i32, BinaryOperator.SHIFT_RIGHT_SIGNED, Opcode.I32_SHRS) { a, b -> a.toInt().shr(b) }
    val I64Shl = ShiftInstr("i64.shl", i64, BinaryOperator.SHIFT_LEFT, Opcode.I64_SHL) { a, b -> a.toLong().shl(b) }
    val I64ShrU = ShiftInstr("i64.shr_u", i64, BinaryOperator.SHIFT_RIGHT_UNSIGNED, Opcode.I64_SHRU) { a, b ->
        a.toLong().ushr(b)
    }
    val I64ShrS =
        ShiftInstr("i64.shr_s", i64, BinaryOperator.SHIFT_RIGHT_SIGNED, Opcode.I64_SHRS) { a, b -> a.toLong().shr(b) }

    val I32And = BinaryI32Instruction("i32.and", BinaryOperator.AND, Opcode.I32_AND) { a, b -> a and b }
    val I64And = BinaryI64Instruction("i64.and", BinaryOperator.AND, Opcode.I64_AND) { a, b -> a and b }
    val I32Or = BinaryI32Instruction("i32.or", BinaryOperator.OR, Opcode.I32_OR) { a, b -> a or b }
    val I64Or = BinaryI64Instruction("i64.or", BinaryOperator.OR, Opcode.I64_OR) { a, b -> a or b }
    val I32XOr = BinaryI32Instruction("i32.xor", BinaryOperator.XOR, Opcode.I32_XOR) { a, b -> a xor b }
    val I64XOr = BinaryI64Instruction("i64.xor", BinaryOperator.XOR, Opcode.I64_XOR) { a, b -> a xor b }

    val F32_ABS = UnaryFloatInstruction("f32.abs", f32, UnaryOperator.ABSOLUTE, Opcode.F32_ABS, ::abs)
    val F64_ABS = UnaryFloatInstruction("f64.abs", f64, UnaryOperator.ABSOLUTE, Opcode.F64_ABS, ::abs)
    val F32_NEG = UnaryFloatInstruction("f32.neg", f32, UnaryOperator.NEGATE, Opcode.F32_NEG, Double::unaryMinus)
    val F64_NEG = UnaryFloatInstruction("f64.neg", f64, UnaryOperator.NEGATE, Opcode.F64_NEG, Double::unaryMinus)
    val F32_MIN = BinaryF32Instruction("f32.min", BinaryOperator.MIN, Opcode.F32_MIN) { a, b -> min(a, b) }
    val F64_MIN = BinaryF64Instruction("f64.min", BinaryOperator.MIN, Opcode.F64_MIN) { a, b -> min(a, b) }
    val F32_MAX = BinaryF32Instruction("f32.max", BinaryOperator.MAX, Opcode.F32_MAX) { a, b -> max(a, b) }
    val F64_MAX = BinaryF64Instruction("f64.max", BinaryOperator.MAX, Opcode.F64_MAX) { a, b -> max(a, b) }
    val F32_SQRT = UnaryFloatInstruction("f32.sqrt", f32, UnaryOperator.SQRT, Opcode.F32_SQRT, ::sqrt)
    val F64_SQRT = UnaryFloatInstruction("f64.sqrt", f64, UnaryOperator.SQRT, Opcode.F64_SQRT, ::sqrt)
    val F32_FLOOR = UnaryFloatInstruction("f32.floor", f32, UnaryOperator.FLOOR, Opcode.F32_FLOOR, ::floor)
    val F64_FLOOR = UnaryFloatInstruction("f64.floor", f64, UnaryOperator.FLOOR, Opcode.F64_FLOOR, ::floor)
    val F32_CEIL = UnaryFloatInstruction("f32.ceil", f32, UnaryOperator.CEIL, Opcode.F32_CEIL, ::ceil)
    val F64_CEIL = UnaryFloatInstruction("f64.ceil", f64, UnaryOperator.CEIL, Opcode.F64_CEIL, ::ceil)
    val F32_NEAREST = UnaryFloatInstruction("f32.nearest", f32, UnaryOperator.ROUND, Opcode.F32_NEAREST, ::round)
    val F64_NEAREST = UnaryFloatInstruction("f64.nearest", f64, UnaryOperator.ROUND, Opcode.F64_NEAREST, ::round)

    val I32_ROTL = BinaryI32Instruction("i32.rotl", BinaryOperator.ROTATE_LEFT, Opcode.I32_ROTL, Int::rotateLeft)
    val I64_ROTL = BinaryI64Instruction(
        "i64.rotl",
        BinaryOperator.ROTATE_LEFT,
        Opcode.I64_ROTL
    ) { a, b -> a.rotateLeft(b.toInt()) }

    val I32_ROTR = BinaryI32Instruction("i32.rotr", BinaryOperator.ROTATE_RIGHT, Opcode.I32_ROTR, Int::rotateRight)
    val I64_ROTR = BinaryI64Instruction(
        "i64.rotr",
        BinaryOperator.ROTATE_RIGHT,
        Opcode.I64_ROTR
    ) { a, b -> a.rotateRight(b.toInt()) }

}