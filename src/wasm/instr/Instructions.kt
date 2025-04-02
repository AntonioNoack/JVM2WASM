package wasm.instr

import utils.WASMTypes.*
import wasm.writer.Opcode
import kotlin.math.*

object Instructions {

    val I32Add = BinaryI32Instruction("i32.add", "+", Opcode.I32_ADD, Int::plus)
    val I32Sub = BinaryI32Instruction("i32.sub", "-", Opcode.I32_SUB, Int::minus)
    val I32Mul = BinaryI32Instruction("i32.mul", "*", Opcode.I32_MUL, Int::times)
    val I32_DIVS = BinaryI32Instruction("i32.div_s", "/", Opcode.I32_DIVS, Int::div)

    val I64Add = BinaryI64Instruction("i64.add", "+", Opcode.I64_ADD, Long::plus)
    val I64Sub = BinaryI64Instruction("i64.sub", "-", Opcode.I64_SUB, Long::minus)
    val I64Mul = BinaryI64Instruction("i64.mul", "*", Opcode.I64_MUL, Long::times)
    val I64_DIVS = BinaryI64Instruction("i64.div_s", "/", Opcode.I64_DIVS, Long::div)

    val F32Add = BinaryF32Instruction("f32.add", "+", Opcode.F32_ADD, Float::plus)
    val F32Sub = BinaryF32Instruction("f32.sub", "-", Opcode.F32_SUB, Float::minus)
    val F32Mul = BinaryF32Instruction("f32.mul", "*", Opcode.F32_MUL, Float::times)
    val F32Div = BinaryF32Instruction("f32.div", "/", Opcode.F32_DIV, Float::div)

    val F64Add = BinaryF64Instruction("f64.add", "+", Opcode.F64_ADD, Double::plus)
    val F64Sub = BinaryF64Instruction("f64.sub", "-", Opcode.F64_SUB, Double::minus)
    val F64Mul = BinaryF64Instruction("f64.mul", "*", Opcode.F64_MUL, Double::times)
    val F64Div = BinaryF64Instruction("f64.div", "/", Opcode.F64_DIV, Double::div)

    val I32Store8 = StoreInstr("i32.store8", i32, 1, Opcode.I32_STORE8) { m, v -> m.put(v.toByte()) }
    val I32Store16 = StoreInstr("i32.store16", i32, 2, Opcode.I32_STORE16) { m, v -> m.putShort(v.toShort()) }
    val I32Store = StoreInstr("i32.store", i32, 4, Opcode.I32_STORE) { m, v -> m.putInt(v.toInt()) }
    val I64Store = StoreInstr("i64.store", i64, 8, Opcode.I64_STORE) { m, v -> m.putLong(v.toLong()) }
    val F32Store = StoreInstr("f32.store", f32, 4, Opcode.F32_STORE) { m, v -> m.putFloat(v.toFloat()) }
    val F64Store = StoreInstr("f64.store", f64, 8, Opcode.F64_STORE) { m, v -> m.putDouble(v.toDouble()) }

    val I32Load8U = LoadInstr("i32.load8_u", i32, 1, Opcode.I32_LOAD8U) { it.get().toInt() }
    val I32Load8S = LoadInstr("i32.load8_s", i32, 1, Opcode.I32_LOAD8S) { it.get().toInt().and(0xff) }
    val I32Load16U = LoadInstr("i32.load16_u", i32, 2, Opcode.I32_LOAD16U) { it.getShort().toInt().and(0xffff) }
    val I32Load16S = LoadInstr("i32.load16_s", i32, 2, Opcode.I32_LOAD16S) { it.getShort() }
    val I32Load = LoadInstr("i32.load", i32, 4, Opcode.I32_LOAD) { it.getInt() }
    val I64Load = LoadInstr("i64.load", i64, 8, Opcode.I64_LOAD) { it.getLong() }
    val F32Load = LoadInstr("f32.load", f32, 4, Opcode.F32_LOAD) { it.getFloat() }
    val F64Load = LoadInstr("f64.load", f64, 8, Opcode.F64_LOAD) { it.getDouble() }

    val F32Trunc = UnaryFloatInstruction("f32.trunc", f32, "std::trunc", Opcode.F32_TRUNC) { truncate(it) }
    val F64Trunc = UnaryFloatInstruction("f64.trunc", f64, "std::trunc", Opcode.F64_TRUNC) { truncate(it) }

    val F32GE = Compare0Instr("f32.ge", ">=", f32, Opcode.F32_GE)
    val F32GT = Compare0Instr("f32.gt", ">", f32, Opcode.F32_GT)
    val F32LE = Compare0Instr("f32.le", "<=", f32, Opcode.F32_LE)
    val F32LT = Compare0Instr("f32.lt", "<", f32, Opcode.F32_LT)
    val F32NE = Compare0Instr("f32.ne", "!=", f32, Opcode.F32_NE)
    val F32EQ = Compare0Instr("f32.eq", "==", f32, Opcode.F32_EQ)

    val F64GE = Compare0Instr("f64.ge", ">=", f64, Opcode.F64_GE)
    val F64GT = Compare0Instr("f64.gt", ">", f64, Opcode.F64_GT)
    val F64LE = Compare0Instr("f64.le", "<=", f64, Opcode.F64_LE)
    val F64LT = Compare0Instr("f64.lt", "<", f64, Opcode.F64_LT)
    val F64NE = Compare0Instr("f64.ne", "!=", f64, Opcode.F64_NE)
    val F64EQ = Compare0Instr("f64.eq", "==", f64, Opcode.F64_EQ)

    val I32_TRUNC_F32S = NumberCastInstruction(
        "i32.trunc_f32_s", "static_cast<i32>(std::trunc(", "))",
        f32, i32, Opcode.I32_TRUNC_F32S, Number::toInt
    )
    val I32_TRUNC_F64S = NumberCastInstruction(
        "i32.trunc_f64_s", "static_cast<i32>(std::trunc(", "))",
        f64, i32, Opcode.I32_TRUNC_F64S, Number::toInt
    )
    val I64_TRUNC_F32S = NumberCastInstruction(
        "i64.trunc_f32_s", "static_cast<i64>(std::trunc(", "))",
        f32, i64, Opcode.I64_TRUNC_F32S, Number::toLong
    )
    val I64_TRUNC_F64S = NumberCastInstruction(
        "i64.trunc_f64_s", "static_cast<i64>(std::trunc(", "))",
        f64, i64, Opcode.I64_TRUNC_F64S, Number::toLong
    )
    val F64_PROMOTE_F32 = NumberCastInstruction(
        "f64.promote_f32", "static_cast<f64>(", ")",
        f32, f64, Opcode.F64_PROMOTE_F32, Number::toDouble
    )
    val F32_DEMOTE_F64 = NumberCastInstruction(
        "f32.demote_f64", "static_cast<f32>(", ")",
        f64, f32, Opcode.F32_DEMOTE_F64, Number::toFloat
    )
    val I64_EXTEND_I32S = NumberCastInstruction(
        "i64.extend_i32_s", "static_cast<i64>(", ")",
        i32, i64, Opcode.I64_EXTEND_I32S, Number::toLong
    )
    val I32_WRAP_I64 = NumberCastInstruction(
        "i32.wrap_i64", "static_cast<i32>(", ")",
        i64, i32, Opcode.I32_WRAP_I64, Number::toInt
    )
    val F32_CONVERT_I32S = NumberCastInstruction(
        "f32.convert_i32_s", "static_cast<f32>(", ")",
        i32, f32, Opcode.F32_CONVERT_I32S, Number::toFloat
    )
    val F32_CONVERT_I64S = NumberCastInstruction(
        "f32.convert_i64_s", "static_cast<f32>(", ")",
        i64, f32, Opcode.F32_CONVERT_I64S, Number::toFloat
    )
    val F64_CONVERT_I32S = NumberCastInstruction(
        "f64.convert_i32_s", "static_cast<f64>(", ")",
        i32, f64, Opcode.F64_CONVERT_I32S, Number::toDouble
    )
    val F64_CONVERT_I64S = NumberCastInstruction(
        "f64.convert_i64_s", "static_cast<f64>(", ")",
        i64, f64, Opcode.F64_CONVERT_I64S, Number::toDouble
    )

    val F64_CONVERT_I32U = NumberCastInstruction(
        "f64.convert_i32_u", "static_cast<f64>(", ")",
        i32, f64, Opcode.F64_CONVERT_I32U
    ) { it.toInt().toUInt().toDouble() }
    val F64_CONVERT_I64U = NumberCastInstruction(
        "f64.convert_i64_u", "static_cast<f64>(", ")",
        i64, f64, Opcode.F64_CONVERT_I64U
    ) { it.toLong().toULong().toDouble() }

    val I32_REINTERPRET_F32 = NumberCastInstruction(
        "i32.reinterpret_f32", "std::bit_cast<i32>(", ")",
        f32, i32, Opcode.I32_REINTERPRET_F32
    ) { it.toFloat().toRawBits() }
    val F32_REINTERPRET_I32 = NumberCastInstruction(
        "f32.reinterpret_i32", "std::bit_cast<f32>(", ")",
        i32, f32, Opcode.F32_REINTERPRET_I32
    ) { Float.fromBits(it.toInt()) }
    val I64_REINTERPRET_F64 = NumberCastInstruction(
        "i64.reinterpret_f64", "std::bit_cast<i64>(", ")",
        f64, i64, Opcode.I64_REINTERPRET_F64
    ) { it.toDouble().toRawBits() }
    val F64_REINTERPRET_I64 = NumberCastInstruction(
        "f64.reinterpret_i64", "std::bit_cast<f64>(", ")",
        i64, f64, Opcode.F64_REINTERPRET_I64
    ) { Double.fromBits(it.toLong()) }

    val I32GES = Compare0Instr("i32.ge_s", ">=", i32, Opcode.I32_GES)
    val I32GTS = Compare0Instr("i32.gt_s", ">", i32, Opcode.I32_GTS)
    val I32LES = Compare0Instr("i32.le_s", "<=", i32, Opcode.I32_LES)
    val I32LTS = Compare0Instr("i32.lt_s", "<", i32, Opcode.I32_LTS)

    val I32GEU = CompareU32Instr("i32.ge_u", ">=", i32, Opcode.I32_GEU)
    val I32GTU = CompareU32Instr("i32.gt_u", ">", i32, Opcode.I32_GTU)
    val I32LEU = CompareU32Instr("i32.le_u", "<=", i32, Opcode.I32_LEU)
    val I32LTU = CompareU32Instr("i32.lt_u", "<", i32, Opcode.I32_LTU)

    val I64GES = Compare0Instr("i64.ge_s", ">=", i64, Opcode.I64_GES)
    val I64GTS = Compare0Instr("i64.gt_s", ">", i64, Opcode.I64_GTS)
    val I64LES = Compare0Instr("i64.le_s", "<=", i64, Opcode.I64_LES)
    val I64LTS = Compare0Instr("i64.lt_s", "<", i64, Opcode.I64_LTS)

    val I32EQZ = EqualsZeroInstruction("i32.eqz", i32, "== 0", Opcode.I32_EQZ)
    val I64EQZ = EqualsZeroInstruction("i64.eqz", i64, "== 0", Opcode.I64_EQZ)

    val I32EQ = Compare0Instr("i32.eq", "==", i32, Opcode.I32_EQ)
    val I32NE = Compare0Instr("i32.ne", "!=", i32, Opcode.I32_NE)
    val I64EQ = Compare0Instr("i64.eq", "==", i64, Opcode.I64_EQ)
    val I64NE = Compare0Instr("i64.ne", "!=", i64, Opcode.I64_NE)

    // todo is this the same for negative numbers in C++, Java and WASM?
    val I32_REM_S = BinaryI32Instruction("i32.rem_s", "%", Opcode.I32_REMS) { a, b -> a % b }
    val I64_REM_S = BinaryI64Instruction("i64.rem_s", "%", Opcode.I64_REMS) { a, b -> a % b }

    val Return = ReturnInstr("return", Opcode.RETURN)
    val Unreachable = ReturnInstr("unreachable", Opcode.UNREACHABLE)

    val I32Shl = ShiftInstr("i32.shl", Opcode.I32_SHL) { a, b -> a.toInt().shl(b) }
    val I32ShrU = ShiftInstr("i32.shr_u", Opcode.I32_SHRU) { a, b -> a.toInt().ushr(b) }
    val I32ShrS = ShiftInstr("i32.shr_s", Opcode.I32_SHRS) { a, b -> a.toInt().shr(b) }
    val I64Shl = ShiftInstr("i64.shl", Opcode.I64_SHL) { a, b -> a.toLong().shl(b) }
    val I64ShrU = ShiftInstr("i64.shr_u", Opcode.I64_SHRU) { a, b -> a.toLong().ushr(b) }
    val I64ShrS = ShiftInstr("i64.shr_s", Opcode.I64_SHRS) { a, b -> a.toLong().shr(b) }

    val I32And = BinaryI32Instruction("i32.and", "&", Opcode.I32_AND) { a, b -> a and b }
    val I64And = BinaryI64Instruction("i64.and", "&", Opcode.I64_AND) { a, b -> a and b }
    val I32Or = BinaryI32Instruction("i32.or", "|", Opcode.I32_OR) { a, b -> a or b }
    val I64Or = BinaryI64Instruction("i64.or", "|", Opcode.I64_OR) { a, b -> a or b }
    val I32XOr = BinaryI32Instruction("i32.xor", "^", Opcode.I32_XOR) { a, b -> a xor b }
    val I64XOr = BinaryI64Instruction("i64.xor", "^", Opcode.I64_XOR) { a, b -> a xor b }

    val F32_ABS = UnaryFloatInstruction("f32.abs", f32, "std::abs", Opcode.F32_ABS, ::abs)
    val F64_ABS = UnaryFloatInstruction("f64.abs", f64, "std::abs", Opcode.F64_ABS, ::abs)
    val F32_NEG = UnaryFloatInstruction("f32.neg", f32, "-", Opcode.F32_NEG, Double::unaryMinus)
    val F64_NEG = UnaryFloatInstruction("f64.neg", f64, "-", Opcode.F64_NEG, Double::unaryMinus)
    val F32_MIN = BinaryF32Instruction("f32.min", "std::min(", Opcode.F32_MIN) { a, b -> min(a, b) }
    val F64_MIN = BinaryF64Instruction("f64.min", "std::min(", Opcode.F64_MIN) { a, b -> min(a, b) }
    val F32_MAX = BinaryF32Instruction("f32.max", "std::max(", Opcode.F32_MAX) { a, b -> max(a, b) }
    val F64_MAX = BinaryF64Instruction("f64.max", "std::max(", Opcode.F64_MAX) { a, b -> max(a, b) }
    val F32_SQRT = UnaryFloatInstruction("f32.sqrt", f32, "std::sqrt", Opcode.F32_SQRT, ::sqrt)
    val F64_SQRT = UnaryFloatInstruction("f64.sqrt", f64, "std::sqrt", Opcode.F64_SQRT, ::sqrt)
    val F32_FLOOR = UnaryFloatInstruction("f32.floor", f32, "std::floor", Opcode.F32_FLOOR, ::floor)
    val F64_FLOOR = UnaryFloatInstruction("f64.floor", f64, "std::floor", Opcode.F64_FLOOR, ::floor)
    val F32_CEIL = UnaryFloatInstruction("f32.ceil", f32, "std::ceil", Opcode.F32_CEIL, ::ceil)
    val F64_CEIL = UnaryFloatInstruction("f64.ceil", f64, "std::ceil", Opcode.F64_CEIL, ::ceil)
    val F32_NEAREST = UnaryFloatInstruction("f32.nearest", f32, "std::round", Opcode.F32_NEAREST, ::round)
    val F64_NEAREST = UnaryFloatInstruction("f64.nearest", f64, "std::round", Opcode.F64_NEAREST, ::round)

    val I32_ROTL = BinaryI32Instruction("i32.rotl", "std::rotl(", Opcode.I32_ROTL, Int::rotateLeft)
    val I64_ROTL = BinaryI64Instruction("i64.rotl", "std::rotl(", Opcode.I64_ROTL) { a, b -> a.rotateLeft(b.toInt()) }

    val I32_ROTR = BinaryI32Instruction("i32.rotr", "std::rotr(", Opcode.I32_ROTR, Int::rotateRight)
    val I64_ROTR = BinaryI64Instruction("i64.rotr", "std::rotr(", Opcode.I64_ROTR) { a, b -> a.rotateRight(b.toInt()) }

}