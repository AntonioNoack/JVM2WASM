package utils

import exportHelpers
import gIndex
import me.anno.utils.assertions.assertNull
import wasm.instr.*
import wasm.instr.Const.Companion.i32Const0
import wasm.instr.Const.Companion.i32Const1
import wasm.instr.Const.Companion.i64Const0
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
import wasm.parser.FunctionImpl
import wasm.parser.LocalVariable

fun appendNativeHelperFunctions() {

    val t = BooleanArray(64) { true }

    fun register(functionImpl: FunctionImpl) {
        assertNull(helperFunctions.put(functionImpl.funcName, functionImpl))
    }

    fun register(name: String, params: List<String>, results: List<String>, instructions: List<Instruction>) {
        register(FunctionImpl(name, params, results, emptyList(), instructions, exportHelpers))
    }

    val types = listOf(i32, i64, f32, f64)
    fun forAll(callback: (String) -> Unit) {
        for (type in types) {
            callback(type)
        }
    }

    fun forAll2(flags: BooleanArray, callback: (String, String) -> Unit) {
        var i = 0
        for (type2 in types) {
            for (type1 in types) {
                if (flags[i++]) callback(type1, type2)
            }
        }
    }

    fun forAll2(callback: (String, String) -> Unit) {
        forAll2(t, callback)
    }

    fun forAll3(flags: BooleanArray, callback: (String, String, String) -> Unit) {
        var i = 0
        for (type3 in types) {
            for (type2 in types) {
                for (type1 in types) {
                    if (flags[i++]) callback(type1, type2, type3)
                }
            }
        }
    }

    forAll { v1 ->
        register("dup$v1", listOf(v1), listOf(v1, v1), listOf(ParamGet[0], ParamGet[0]))
    }
    forAll2 { v1, v2 ->
        register(
            "dup2$v1$v2", listOf(v1, v2), listOf(v1, v2, v1, v2),
            listOf(ParamGet[0], ParamGet[1], ParamGet[0], ParamGet[1])
        )
        register(
            "swap$v1$v2", listOf(v1, v2), listOf(v2, v1),
            listOf(ParamGet[1], ParamGet[0])
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
            listOf(ParamGet[0], ParamGet[2], I32Add, ParamGet[1], storeInstr)
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
            listOf(ParamGet[1], ParamGet[0], storeInstr)
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
            listOf(ParamGet[0], ParamGet[1], I32Add, loadInstr)
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
            listOf(ParamGet[0], loadInstr)
        )
    }

    forAll2(gIndex.usedDup_x1) { v1, v2 ->
        register(
            "dup_x1$v1$v2", listOf(v2, v1), listOf(v1, v2, v1),
            listOf(ParamGet[1], ParamGet[0], ParamGet[1])
        )
    }

    forAll3(gIndex.usedDup_x2) { v1, v2, v3 ->
        register(
            "dup_x2$v1$v2$v3", listOf(v3, v2, v1), listOf(v1, v3, v2, v1),
            listOf(ParamGet[2], ParamGet[0], ParamGet[1], ParamGet[2])
        )
    }

    // a % b = a - a/b*b
    register(
        "f32rem", listOf(f32, f32), listOf(f32),
        listOf(ParamGet[0], ParamGet[0], ParamGet[1], F32Div, ParamGet[1], F32Trunc, F32Mul, F32Sub)
    )
    register(
        "f64rem", listOf(f64, f64), listOf(f64),
        listOf(ParamGet[0], ParamGet[0], ParamGet[1], F64Div, ParamGet[1], F64Trunc, F64Mul, F64Sub)
    )

    register("i32neg", listOf(i32), listOf(i32), listOf(i32Const0, ParamGet[0], I32Sub))
    register("i64neg", listOf(i64), listOf(i64), listOf(i64Const0, ParamGet[0], I64Sub))
    register(
        "lcmp", listOf(i64, i64), listOf(i32), listOf(
            ParamGet[0], ParamGet[1], I64GTS,
            ParamGet[0], ParamGet[1], I64LTS, I32Sub
        )
    )

    // to do implement this, when we have multi-threading
    register("monitorEnter", listOf(ptrType), emptyList(), emptyList())
    register("monitorExit", listOf(ptrType), emptyList(), emptyList())
    register(FunctionImpl("wasStaticInited", listOf(i32), listOf(i32),
        listOf(LocalVariable("addr", i32)),
        listOf(
            GlobalGet("Z"), ParamGet[0], I32Add, LocalSet("addr"), // calculate flag address
            LocalGet("addr"), I32Load8S, // load result (unused by next line)
            LocalGet("addr"), i32Const1, I32Store8 // set flag
            // return result
        ),
        exportHelpers))
}