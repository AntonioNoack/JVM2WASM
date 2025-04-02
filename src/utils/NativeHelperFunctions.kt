package utils

import exportHelpers
import me.anno.utils.assertions.assertNull
import utils.Param.Companion.toParams
import utils.WASMTypes.*
import wasm.instr.Call
import wasm.instr.Const.Companion.i32Const0
import wasm.instr.Const.Companion.i64Const0
import wasm.instr.Instruction
import wasm.instr.Instructions.F32Div
import wasm.instr.Instructions.F32Load
import wasm.instr.Instructions.F32Mul
import wasm.instr.Instructions.F32Store
import wasm.instr.Instructions.F32Sub
import wasm.instr.Instructions.F32Trunc
import wasm.instr.Instructions.F64Div
import wasm.instr.Instructions.F64Load
import wasm.instr.Instructions.F64Mul
import wasm.instr.Instructions.F64Store
import wasm.instr.Instructions.F64Sub
import wasm.instr.Instructions.F64Trunc
import wasm.instr.Instructions.I32Add
import wasm.instr.Instructions.I32Load
import wasm.instr.Instructions.I32Load16S
import wasm.instr.Instructions.I32Load16U
import wasm.instr.Instructions.I32Load8S
import wasm.instr.Instructions.I32Store
import wasm.instr.Instructions.I32Store16
import wasm.instr.Instructions.I32Store8
import wasm.instr.Instructions.I32Sub
import wasm.instr.Instructions.I64GTS
import wasm.instr.Instructions.I64LTS
import wasm.instr.Instructions.I64Load
import wasm.instr.Instructions.I64Store
import wasm.instr.Instructions.I64Sub
import wasm.instr.Instructions.Return
import wasm.instr.ParamGet
import wasm.parser.FunctionImpl

object NativeHelperFunctions {

    private val types = listOf(i32, i64, f32, f64)
    fun register(functionImpl: FunctionImpl) {
        assertNull(helperFunctions.put(functionImpl.funcName, functionImpl))
    }

    fun register(name: String, params: List<String>, results: List<String>, instructions: List<Instruction>) {
        register(FunctionImpl(name, params.toParams(), results, emptyList(), ArrayList(instructions), exportHelpers))
    }

    private fun forAll(callback: (String) -> Unit) {
        for (type in types) {
            callback(type)
        }
    }

    private fun forAll2(callback: (String, String) -> Unit) {
        for (type1 in types) {
            for (type2 in types) {
                callback(type1, type2)
            }
        }
    }

    private fun forAll3(callback: (String, String, String) -> Unit) {
        for (type1 in types) {
            for (type2 in types) {
                for (type3 in types) {
                    callback(type1, type2, type3)
                }
            }
        }
    }

    fun appendNativeHelperFunctions() {

        forAll { v1 ->
            register("dup$v1", listOf(v1), listOf(v1, v1), listOf(ParamGet[0], ParamGet[0], Return))
        }
        forAll2 { v1, v2 ->
            register(
                "dup2$v1$v2", listOf(v1, v2), listOf(v1, v2, v1, v2),
                listOf(ParamGet[0], ParamGet[1], ParamGet[0], ParamGet[1], Return)
            )
            register(
                "swap$v1$v2", listOf(v1, v2), listOf(v2, v1),
                listOf(ParamGet[1], ParamGet[0], Return)
            )
        }

        // setField-functions
        for ((call, type, storeInstr) in listOf(
            Triple(Call.setFieldI8, i32, I32Store8),
            Triple(Call.setFieldI16, i32, I32Store16),
            Triple(Call.setFieldI32, i32, I32Store),
            Triple(Call.setFieldI64, i64, I64Store),
            Triple(Call.setFieldF32, f32, F32Store),
            Triple(Call.setFieldF64, f64, F64Store),
        )) {
            register(
                call.name, listOf(i32, type, i32), emptyList(),
                listOf(
                    // ParamGet[0], ParamGet[2], i32Const(storeInstr.numBytes), Call.checkWrite,

                    ParamGet[0], ParamGet[2], I32Add,
                    ParamGet[1], storeInstr, Return)
            )
        }

        // set(Value,Instance,Offset)Field-functions
        for ((call, type, storeInstr) in listOf(
            Triple(Call.setVIOFieldI8, i32, I32Store8),
            Triple(Call.setVIOFieldI16, i32, I32Store16),
            Triple(Call.setVIOFieldI32, i32, I32Store),
            Triple(Call.setVIOFieldI64, i64, I64Store),
            Triple(Call.setVIOFieldF32, f32, F32Store),
            Triple(Call.setVIOFieldF64, f64, F64Store),
        )) {
            register(
                call.name, listOf(type, i32, i32), emptyList(),
                listOf(
                    // ParamGet[1], ParamGet[2], i32Const(storeInstr.numBytes), Call.checkWrite,

                    ParamGet[1], ParamGet[2], I32Add,
                    ParamGet[0], storeInstr, Return
                )
            )
        }

        // setStaticField-functions
        for ((call, type, storeInstr) in listOf(
            Triple(Call.setStaticFieldI8, i32, I32Store8),
            Triple(Call.setStaticFieldI16, i32, I32Store16),
            Triple(Call.setStaticFieldI32, i32, I32Store),
            Triple(Call.setStaticFieldI64, i64, I64Store),
            Triple(Call.setStaticFieldF32, f32, F32Store),
            Triple(Call.setStaticFieldF64, f64, F64Store),
        )) {
            register(
                call.name, listOf(type, i32), emptyList(),
                listOf(
                    // i32Const0, ParamGet[1], i32Const(storeInstr.numBytes), Call.checkWrite,

                    ParamGet[1], ParamGet[0], storeInstr, Return
                )
            )
        }

        // getField-functions
        for ((call, type, loadInstr) in listOf(
            Triple(Call.getFieldS8, i32, I32Load8S),
            Triple(Call.getFieldS16, i32, I32Load16S),
            Triple(Call.getFieldU16, i32, I32Load16U),
            Triple(Call.getFieldI32, i32, I32Load),
            Triple(Call.getFieldI64, i64, I64Load),
            Triple(Call.getFieldF32, f32, F32Load),
            Triple(Call.getFieldF64, f64, F64Load),
        )) {
            register(
                call.name, listOf(i32, i32), listOf(type),
                listOf(ParamGet[0], ParamGet[1], I32Add, loadInstr, Return)
            )
        }

        // getStaticField-functions
        for ((call, type, loadInstr) in listOf(
            Triple(Call.getStaticFieldS8, i32, I32Load8S),
            Triple(Call.getStaticFieldS16, i32, I32Load16S),
            Triple(Call.getStaticFieldU16, i32, I32Load16U),
            Triple(Call.getStaticFieldI32, i32, I32Load),
            Triple(Call.getStaticFieldI64, i64, I64Load),
            Triple(Call.getStaticFieldF32, f32, F32Load),
            Triple(Call.getStaticFieldF64, f64, F64Load),
        )) {
            register(
                call.name, listOf(i32), listOf(type),
                listOf(ParamGet[0], loadInstr, Return)
            )
        }

        forAll2 { v1, v2 ->
            register(
                "dup_x1$v1$v2", listOf(v2, v1), listOf(v1, v2, v1),
                listOf(ParamGet[1], ParamGet[0], ParamGet[1], Return)
            )
        }

        forAll3 { v1, v2, v3 ->
            register(
                "dup_x2$v1$v2$v3", listOf(v3, v2, v1), listOf(v1, v3, v2, v1),
                listOf(ParamGet[2], ParamGet[0], ParamGet[1], ParamGet[2], Return)
            )
        }

        // a % b = a - a/b*b
        register(
            "f32rem", listOf(f32, f32), listOf(f32),
            listOf(ParamGet[0], ParamGet[0], ParamGet[1], F32Div, ParamGet[1], F32Trunc, F32Mul, F32Sub, Return)
        )
        register(
            "f64rem", listOf(f64, f64), listOf(f64),
            listOf(ParamGet[0], ParamGet[0], ParamGet[1], F64Div, ParamGet[1], F64Trunc, F64Mul, F64Sub, Return)
        )

        register("i32neg", listOf(i32), listOf(i32), listOf(i32Const0, ParamGet[0], I32Sub, Return))
        register("i64neg", listOf(i64), listOf(i64), listOf(i64Const0, ParamGet[0], I64Sub, Return))
        register(
            Call.lcmp.name, listOf(i64, i64), listOf(i32), listOf(
                ParamGet[0], ParamGet[1], I64GTS,
                ParamGet[0], ParamGet[1], I64LTS, I32Sub, Return
            )
        )
    }
}