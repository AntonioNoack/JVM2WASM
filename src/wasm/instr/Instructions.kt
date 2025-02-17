package wasm.instr

object Instructions {

    val I32Add = BinaryInstruction("i32.add", "+")
    val I32Sub = BinaryInstruction("i32.sub", "-")
    val I32Mul = BinaryInstruction("i32.mul", "*")
    val I32_DIVS = BinaryInstruction("i32.div_s", "/")

    val I64Add = BinaryInstruction("i64.add", "+")
    val I64Sub = BinaryInstruction("i64.sub", "-")
    val I64Mul = BinaryInstruction("i64.mul", "*")
    val I64_DIVS = BinaryInstruction("i64.div_s", "/")

    val F32Add = BinaryInstruction("f32.add", "+")
    val F32Sub = BinaryInstruction("f32.sub", "-")
    val F32Mul = BinaryInstruction("f32.mul", "*")
    val F32Div = BinaryInstruction("f32.div", "/")

    val F64Add = BinaryInstruction("f64.add", "+")
    val F64Sub = BinaryInstruction("f64.sub", "-")
    val F64Mul = BinaryInstruction("f64.mul", "*")
    val F64Div = BinaryInstruction("f64.div", "/")

    val I32Store8 = SimpleInstr("i32.store8")
    val I32Store16 = SimpleInstr("i32.store16")
    val I32Store = SimpleInstr("i32.store")
    val I64Store = SimpleInstr("i64.store")
    val F32Store = SimpleInstr("f32.store")
    val F64Store = SimpleInstr("f64.store")

    val I32Load8U = SimpleInstr("i32.load8_u")
    val I32Load8S = SimpleInstr("i32.load8_s")
    val I32Load16U = SimpleInstr("i32.load16_u")
    val I32Load16S = SimpleInstr("i32.load16_s")
    val I32Load = SimpleInstr("i32.load")
    val I64Load = SimpleInstr("i64.load")
    val F32Load = SimpleInstr("f32.load")
    val F64Load = SimpleInstr("f64.load")

    val F32Trunc = UnaryInstruction("f32.trunc", "std::trunc")
    val F64Trunc = UnaryInstruction("f64.trunc", "std::trunc")

    val F32GE = CompareInstr("f32.ge", ">=")
    val F32GT = CompareInstr("f32.gt", ">")
    val F32LE = CompareInstr("f32.le", "<=")
    val F32LT = CompareInstr("f32.lt", "<")
    val F32NE = CompareInstr("f32.ne", "!=")
    val F32EQ = CompareInstr("f32.eq", "==")

    val F64GE = CompareInstr("f64.ge", ">=")
    val F64GT = CompareInstr("f64.gt", ">")
    val F64LE = CompareInstr("f64.le", "<=")
    val F64LT = CompareInstr("f64.lt", "<")
    val F64NE = CompareInstr("f64.ne", "!=")
    val F64EQ = CompareInstr("f64.eq", "==")

    val I32_TRUNC_F32S = UnaryInstruction2("i32.trunc_f32_s", "static_cast<i32>(std::trunc(", "))", "f32")
    val I32_TRUNC_F64S = UnaryInstruction2("i32.trunc_f64_s", "static_cast<i32>(std::trunc(", "))", "f64")
    val I64_TRUNC_F32S = UnaryInstruction2("i64.trunc_f32_s", "static_cast<i64>(std::trunc(", "))", "f32")
    val I64_TRUNC_F64S = UnaryInstruction2("i64.trunc_f64_s", "static_cast<i64>(std::trunc(", "))", "f64")
    val F64_PROMOTE_F32 = UnaryInstruction2("f64.promote_f32", "static_cast<f64>(", ")", "f32")
    val F32_DEMOTE_F64 = UnaryInstruction2("f32.demote_f64", "static_cast<f32>(", ")", "f64")
    val I64_EXTEND_I32S = UnaryInstruction2("i64.extend_i32_s", "static_cast<i64>(", ")", "i32")
    val I32_WRAP_I64 = UnaryInstruction2("i32.wrap_i64", "static_cast<i32>(", ")", "i64")
    val F32_CONVERT_I32S = UnaryInstruction2("f32.convert_i32_s", "static_cast<f32>(", ")", "i32")
    val F32_CONVERT_I64S = UnaryInstruction2("f32.convert_i64_s", "static_cast<f32>(", ")", "i64")
    val F64_CONVERT_I32S = UnaryInstruction2("f64.convert_i32_s", "static_cast<f64>(", ")", "i32")
    val F64_CONVERT_I64S = UnaryInstruction2("f64.convert_i64_s", "static_cast<f64>(", ")", "i64")

    val F64_CONVERT_I32U = UnaryInstruction2("f64.convert_i32_u", "static_cast<f64>(", ")", "i32")
    val F64_CONVERT_I64U = UnaryInstruction2("f64.convert_i64_u", "static_cast<f64>(", ")", "i64")

    val I32_REINTERPRET_F32 = UnaryInstruction2("i32.reinterpret_f32", "std::bit_cast<i32>(", ")", "f32")
    val F32_REINTERPRET_I32 = UnaryInstruction2("f32.reinterpret_i32", "std::bit_cast<f32>(", ")", "i32")
    val I64_REINTERPRET_F64 = UnaryInstruction2("i64.reinterpret_f64", "std::bit_cast<i64>(", ")", "f64")
    val F64_REINTERPRET_I64 = UnaryInstruction2("f64.reinterpret_i64", "std::bit_cast<f64>(", ")", "i64")

    val I32GES = CompareInstr("i32.ge_s", ">=")
    val I32GTS = CompareInstr("i32.gt_s", ">")
    val I32LES = CompareInstr("i32.le_s", "<=")
    val I32LTS = CompareInstr("i32.lt_s", "<")

    val I32GEU = CompareInstr("i32.ge_u", ">=", "u32")
    val I32GTU = CompareInstr("i32.gt_u", ">", "u32")
    val I32LEU = CompareInstr("i32.le_u", "<=", "u32")
    val I32LTU = CompareInstr("i32.lt_u", "<", "u32")

    val I64GES = CompareInstr("i64.ge_s", ">=")
    val I64GTS = CompareInstr("i64.gt_s", ">")
    val I64LES = CompareInstr("i64.le_s", "<=")
    val I64LTS = CompareInstr("i64.lt_s", "<")

    val I32EQZ = SimpleInstr("i32.eqz")
    val I64EQZ = SimpleInstr("i64.eqz")

    val I32EQ = CompareInstr("i32.eq", "==")
    val I32NE = CompareInstr("i32.ne", "!=")
    val I64EQ = CompareInstr("i64.eq", "==")
    val I64NE = CompareInstr("i64.ne", "!=")

    // according to ChatGPT, they have the same behaviour
    val I32_REM_S = BinaryInstruction("i32.rem_s", "%")
    val I64_REM_S = BinaryInstruction("i64.rem_s", "%")

    val Return = SimpleInstr("return")
    val Unreachable = SimpleInstr("unreachable")

    val I32Shl = ShiftInstr("i32.shl")
    val I32ShrU = ShiftInstr("i32.shr_u")
    val I32ShrS = ShiftInstr("i32.shr_s")
    val I64Shl = ShiftInstr("i64.shl")
    val I64ShrU = ShiftInstr("i64.shr_u")
    val I64ShrS = ShiftInstr("i64.shr_s")

    val I32And = BinaryInstruction("i32.and", "&")
    val I64And = BinaryInstruction("i64.and", "&")
    val I32Or = BinaryInstruction("i32.or", "|")
    val I64Or = BinaryInstruction("i64.or", "|")
    val I32XOr = BinaryInstruction("i32.xor", "^")
    val I64XOr = BinaryInstruction("i64.xor", "^")

    val F32_ABS = UnaryInstruction("f32.abs", "std::abs")
    val F64_ABS = UnaryInstruction("f64.abs", "std::abs")
    val F32_NEG = UnaryInstruction("f32.neg", "-")
    val F64_NEG = UnaryInstruction("f64.neg", "-")
    val F32_MIN = BinaryInstruction("f32.min", "std::min(")
    val F64_MIN = BinaryInstruction("f64.min", "std::min(")
    val F32_MAX = BinaryInstruction("f32.max", "std::max(")
    val F64_MAX = BinaryInstruction("f64.max", "std::max(")
    val F32_SQRT = UnaryInstruction("f32.sqrt", "std::sqrt")
    val F64_SQRT = UnaryInstruction("f64.sqrt", "std::sqrt")
    val F32_FLOOR = UnaryInstruction("f32.floor", "std::floor")
    val F64_FLOOR = UnaryInstruction("f64.floor", "std::floor")
    val F32_CEIL = UnaryInstruction("f32.ceil", "std::ceil")
    val F64_CEIL = UnaryInstruction("f64.ceil", "std::ceil")
    val F32_NEAREST = UnaryInstruction("f32.nearest", "std::round")
    val F64_NEAREST = UnaryInstruction("f64.nearest", "std::round")

    val I32_ROTL = BinaryInstruction("i32.rotl", "std::rotl(")
    val I64_ROTL = BinaryInstruction("i64.rotl", "std::rotl(")

    val I32_ROTR = BinaryInstruction("i32.rotr", "std::rotr(")
    val I64_ROTR = BinaryInstruction("i64.rotr", "std::rotr(")

    val Drop = SimpleInstr("drop")

}