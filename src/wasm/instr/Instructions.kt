package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.pop
import utils.WASMTypes.*
import kotlin.math.*

object Instructions {

    val I32Add = BinaryI32Instruction("i32.add", "+", Int::plus)
    val I32Sub = BinaryI32Instruction("i32.sub", "-", Int::minus)
    val I32Mul = BinaryI32Instruction("i32.mul", "*", Int::times)
    val I32_DIVS = BinaryI32Instruction("i32.div_s", "/", Int::div)

    val I64Add = BinaryI64Instruction("i64.add", "+", Long::plus)
    val I64Sub = BinaryI64Instruction("i64.sub", "-", Long::minus)
    val I64Mul = BinaryI64Instruction("i64.mul", "*", Long::times)
    val I64_DIVS = BinaryI64Instruction("i64.div_s", "/", Long::div)

    val F32Add = BinaryF32Instruction("f32.add", "+", Float::plus)
    val F32Sub = BinaryF32Instruction("f32.sub", "-", Float::minus)
    val F32Mul = BinaryF32Instruction("f32.mul", "*", Float::times)
    val F32Div = BinaryF32Instruction("f32.div", "/", Float::div)

    val F64Add = BinaryF64Instruction("f64.add", "+", Double::plus)
    val F64Sub = BinaryF64Instruction("f64.sub", "-", Double::minus)
    val F64Mul = BinaryF64Instruction("f64.mul", "*", Double::times)
    val F64Div = BinaryF64Instruction("f64.div", "/", Double::div)

    val I32Store8 = StoreInstr("i32.store8") { m, v -> m.put(v.toByte()) }
    val I32Store16 = StoreInstr("i32.store16") { m, v -> m.putShort(v.toShort()) }
    val I32Store = StoreInstr("i32.store") { m, v -> m.putInt(v.toInt()) }
    val I64Store = StoreInstr("i64.store") { m, v -> m.putLong(v.toLong()) }
    val F32Store = StoreInstr("f32.store") { m, v -> m.putFloat(v.toFloat()) }
    val F64Store = StoreInstr("f64.store") { m, v -> m.putDouble(v.toDouble()) }

    val I32Load8U = LoadInstr("i32.load8_u") { it.get().toInt() }
    val I32Load8S = LoadInstr("i32.load8_s") { it.get().toInt().and(0xff) }
    val I32Load16U = LoadInstr("i32.load16_u") { it.getShort().toInt().and(0xffff) }
    val I32Load16S = LoadInstr("i32.load16_s") { it.getShort() }
    val I32Load = LoadInstr("i32.load") { it.getInt() }
    val I64Load = LoadInstr("i64.load") { it.getLong() }
    val F32Load = LoadInstr("f32.load") { it.getFloat() }
    val F64Load = LoadInstr("f64.load") { it.getDouble() }

    val F32Trunc = UnaryFloatInstruction("f32.trunc", f32, "std::trunc") { truncate(it) }
    val F64Trunc = UnaryFloatInstruction("f64.trunc", f64, "std::trunc") { truncate(it) }

    val F32GE = Compare0Instr("f32.ge", ">=", f32)
    val F32GT = Compare0Instr("f32.gt", ">", f32)
    val F32LE = Compare0Instr("f32.le", "<=", f32)
    val F32LT = Compare0Instr("f32.lt", "<", f32)
    val F32NE = Compare0Instr("f32.ne", "!=", f32)
    val F32EQ = Compare0Instr("f32.eq", "==", f32)

    val F64GE = Compare0Instr("f64.ge", ">=", f64)
    val F64GT = Compare0Instr("f64.gt", ">", f64)
    val F64LE = Compare0Instr("f64.le", "<=", f64)
    val F64LT = Compare0Instr("f64.lt", "<", f64)
    val F64NE = Compare0Instr("f64.ne", "!=", f64)
    val F64EQ = Compare0Instr("f64.eq", "==", f64)

    val I32_TRUNC_F32S = NumberCastInstruction(
        "i32.trunc_f32_s", "static_cast<i32>(std::trunc(", "))",
        f32, i32, Number::toInt
    )
    val I32_TRUNC_F64S = NumberCastInstruction(
        "i32.trunc_f64_s", "static_cast<i32>(std::trunc(", "))",
        f64, i32, Number::toInt
    )
    val I64_TRUNC_F32S = NumberCastInstruction(
        "i64.trunc_f32_s", "static_cast<i64>(std::trunc(", "))",
        f32, i64, Number::toLong
    )
    val I64_TRUNC_F64S = NumberCastInstruction(
        "i64.trunc_f64_s", "static_cast<i64>(std::trunc(", "))",
        f64, i64, Number::toLong
    )
    val F64_PROMOTE_F32 = NumberCastInstruction(
        "f64.promote_f32", "static_cast<f64>(", ")",
        f32, f64, Number::toDouble
    )
    val F32_DEMOTE_F64 = NumberCastInstruction(
        "f32.demote_f64", "static_cast<f32>(", ")",
        f64, f32, Number::toFloat
    )
    val I64_EXTEND_I32S = NumberCastInstruction(
        "i64.extend_i32_s", "static_cast<i64>(", ")",
        i32, i64, Number::toLong
    )
    val I32_WRAP_I64 = NumberCastInstruction(
        "i32.wrap_i64", "static_cast<i32>(", ")",
        i64, i32, Number::toInt
    )
    val F32_CONVERT_I32S = NumberCastInstruction(
        "f32.convert_i32_s", "static_cast<f32>(", ")",
        i32, f32, Number::toFloat
    )
    val F32_CONVERT_I64S = NumberCastInstruction(
        "f32.convert_i64_s", "static_cast<f32>(", ")",
        i64, f32, Number::toFloat
    )
    val F64_CONVERT_I32S = NumberCastInstruction(
        "f64.convert_i32_s", "static_cast<f64>(", ")",
        i32, f64, Number::toDouble
    )
    val F64_CONVERT_I64S = NumberCastInstruction(
        "f64.convert_i64_s", "static_cast<f64>(", ")",
        i64, f64, Number::toDouble
    )

    val F64_CONVERT_I32U = NumberCastInstruction(
        "f64.convert_i32_u", "static_cast<f64>(", ")",
        i32, f64
    ) { it.toInt().toUInt().toDouble() }
    val F64_CONVERT_I64U = NumberCastInstruction(
        "f64.convert_i64_u", "static_cast<f64>(", ")",
        i64, f64
    ) { it.toLong().toULong().toDouble() }

    val I32_REINTERPRET_F32 = NumberCastInstruction(
        "i32.reinterpret_f32", "std::bit_cast<i32>(", ")",
        f32, i32
    ) { it.toFloat().toRawBits() }
    val F32_REINTERPRET_I32 = NumberCastInstruction(
        "f32.reinterpret_i32", "std::bit_cast<f32>(", ")",
        i32, f32
    ) { Float.fromBits(it.toInt()) }
    val I64_REINTERPRET_F64 = NumberCastInstruction(
        "i64.reinterpret_f64", "std::bit_cast<i64>(", ")",
        f64, i64
    ) { it.toDouble().toRawBits() }
    val F64_REINTERPRET_I64 = NumberCastInstruction(
        "f64.reinterpret_i64", "std::bit_cast<f64>(", ")",
        i64, f64
    ) { Double.fromBits(it.toLong()) }

    val I32GES = Compare0Instr("i32.ge_s", ">=", i32)
    val I32GTS = Compare0Instr("i32.gt_s", ">", i32)
    val I32LES = Compare0Instr("i32.le_s", "<=", i32)
    val I32LTS = Compare0Instr("i32.lt_s", "<", i32)

    val I32GEU = CompareU32Instr("i32.ge_u", ">=", i32)
    val I32GTU = CompareU32Instr("i32.gt_u", ">", i32)
    val I32LEU = CompareU32Instr("i32.le_u", "<=", i32)
    val I32LTU = CompareU32Instr("i32.lt_u", "<", i32)

    val I64GES = Compare0Instr("i64.ge_s", ">=", i64)
    val I64GTS = Compare0Instr("i64.gt_s", ">", i64)
    val I64LES = Compare0Instr("i64.le_s", "<=", i64)
    val I64LTS = Compare0Instr("i64.lt_s", "<", i64)

    val I32EQZ = EqualsZeroInstruction("i32.eqz", i32, "== 0")
    val I64EQZ = EqualsZeroInstruction("i64.eqz", i64, "== 0")

    val I32EQ = Compare0Instr("i32.eq", "==", i32)
    val I32NE = Compare0Instr("i32.ne", "!=", i32)
    val I64EQ = Compare0Instr("i64.eq", "==", i64)
    val I64NE = Compare0Instr("i64.ne", "!=", i64)

    // according to ChatGPT, they have the same behaviour
    val I32_REM_S = BinaryI32Instruction("i32.rem_s", "%") { a, b -> a % b } // todo is this correct???
    val I64_REM_S = BinaryI64Instruction("i64.rem_s", "%") { a, b -> a % b } // todo is this correct???

    val Return = ReturnInstr("return")
    val Unreachable = ReturnInstr("unreachable")

    val I32Shl = ShiftInstr("i32.shl") { a, b -> a.toInt().shl(b) }
    val I32ShrU = ShiftInstr("i32.shr_u") { a, b -> a.toInt().ushr(b) }
    val I32ShrS = ShiftInstr("i32.shr_s") { a, b -> a.toInt().shr(b) }
    val I64Shl = ShiftInstr("i64.shl") { a, b -> a.toLong().shl(b) }
    val I64ShrU = ShiftInstr("i64.shr_u") { a, b -> a.toLong().ushr(b) }
    val I64ShrS = ShiftInstr("i64.shr_s") { a, b -> a.toLong().shr(b) }

    val I32And = BinaryI32Instruction("i32.and", "&") { a, b -> a and b }
    val I64And = BinaryI64Instruction("i64.and", "&") { a, b -> a and b }
    val I32Or = BinaryI32Instruction("i32.or", "|") { a, b -> a or b }
    val I64Or = BinaryI64Instruction("i64.or", "|") { a, b -> a or b }
    val I32XOr = BinaryI32Instruction("i32.xor", "^") { a, b -> a xor b }
    val I64XOr = BinaryI64Instruction("i64.xor", "^") { a, b -> a xor b }

    val F32_ABS = UnaryFloatInstruction("f32.abs", f32, "std::abs", ::abs)
    val F64_ABS = UnaryFloatInstruction("f64.abs", f64, "std::abs", ::abs)
    val F32_NEG = UnaryFloatInstruction("f32.neg", f32, "-", Double::unaryMinus)
    val F64_NEG = UnaryFloatInstruction("f64.neg", f64, "-", Double::unaryMinus)
    val F32_MIN = BinaryF32Instruction("f32.min", "std::min(") { a, b -> min(a, b) }
    val F64_MIN = BinaryF64Instruction("f64.min", "std::min(") { a, b -> min(a, b) }
    val F32_MAX = BinaryF32Instruction("f32.max", "std::max(") { a, b -> max(a, b) }
    val F64_MAX = BinaryF64Instruction("f64.max", "std::max(") { a, b -> max(a, b) }
    val F32_SQRT = UnaryFloatInstruction("f32.sqrt", f32, "std::sqrt", ::sqrt)
    val F64_SQRT = UnaryFloatInstruction("f64.sqrt", f64, "std::sqrt", ::sqrt)
    val F32_FLOOR = UnaryFloatInstruction("f32.floor", f32, "std::floor", ::floor)
    val F64_FLOOR = UnaryFloatInstruction("f64.floor", f64, "std::floor", ::floor)
    val F32_CEIL = UnaryFloatInstruction("f32.ceil", f32, "std::ceil", ::ceil)
    val F64_CEIL = UnaryFloatInstruction("f64.ceil", f64, "std::ceil", ::ceil)
    val F32_NEAREST = UnaryFloatInstruction("f32.nearest", f32, "std::round", ::round)
    val F64_NEAREST = UnaryFloatInstruction("f64.nearest", f64, "std::round", ::round)

    val I32_ROTL = BinaryI32Instruction("i32.rotl", "std::rotl(", Int::rotateLeft)
    val I64_ROTL = BinaryI64Instruction("i64.rotl", "std::rotl(") { a, b -> a.rotateLeft(b.toInt()) }

    val I32_ROTR = BinaryI32Instruction("i32.rotr", "std::rotr(", Int::rotateRight)
    val I64_ROTR = BinaryI64Instruction("i64.rotr", "std::rotr(") { a, b -> a.rotateRight(b.toInt()) }

    val Drop = object : SimpleInstr("drop") {
        override fun execute(engine: WASMEngine): String? {
            engine.pop()
            return null
        }
    }

}