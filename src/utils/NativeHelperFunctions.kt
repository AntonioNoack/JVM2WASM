package utils

import exportHelpers
import jvm.JVMFlags.is32Bits
import me.anno.utils.assertions.assertTrue
import utils.Param.Companion.toParams
import utils.WASMTypes.*
import wasm.instr.Call
import wasm.instr.Const.Companion.i32Const0
import wasm.instr.Const.Companion.i32Const1
import wasm.instr.Const.Companion.i32ConstM1
import wasm.instr.Const.Companion.i64Const0
import wasm.instr.IfBranch
import wasm.instr.Instruction
import wasm.instr.Instructions.F32Div
import wasm.instr.Instructions.F32EQ
import wasm.instr.Instructions.F32GT
import wasm.instr.Instructions.F32LT
import wasm.instr.Instructions.F32Load
import wasm.instr.Instructions.F32Mul
import wasm.instr.Instructions.F32Store
import wasm.instr.Instructions.F32Sub
import wasm.instr.Instructions.F32Trunc
import wasm.instr.Instructions.F64Div
import wasm.instr.Instructions.F64EQ
import wasm.instr.Instructions.F64GT
import wasm.instr.Instructions.F64LT
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
import wasm.instr.Instructions.I64Add
import wasm.instr.Instructions.I64GTS
import wasm.instr.Instructions.I64LTS
import wasm.instr.Instructions.I64Load
import wasm.instr.Instructions.I64Store
import wasm.instr.Instructions.I64Sub
import wasm.instr.Instructions.I64_EXTEND_I32S
import wasm.instr.Instructions.Return
import wasm.instr.Instructions.Unreachable
import wasm.instr.ParamGet
import wasm.parser.FunctionImpl

object NativeHelperFunctions {

    private val types = listOf(i32, i64, f32, f64)
    fun register(func: FunctionImpl) {
        val old = helperFunctions.put(func.funcName, func)
        assertTrue(old == null) {
            "Found duplicate implementation for ${func.funcName}"
        }
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

        // setField-functions (instance,value,offset)
        for ((call, type, storeInstr) in listOf(
            Triple(Call.setFieldI8, i32, I32Store8),
            Triple(Call.setFieldI16, i32, I32Store16),
            Triple(Call.setFieldI32, i32, I32Store),
            Triple(Call.setFieldI64, i64, I64Store),
            Triple(Call.setFieldF32, f32, F32Store),
            Triple(Call.setFieldF64, f64, F64Store),
        )) {
            register(
                call.name, listOf(ptrType, type, i32), emptyList(),
                if (is32Bits) {
                    listOf(
                        // ParamGet[0], ParamGet[2], i32Const(storeInstr.numBytes), Call.checkWrite,

                        ParamGet[0], ParamGet[2], I32Add,
                        ParamGet[1], storeInstr, Return
                    )
                } else {
                    listOf(
                        ParamGet[0], ParamGet[2], I64_EXTEND_I32S, I64Add,
                        ParamGet[1], storeInstr, Return
                    )
                }
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
                call.name, listOf(type, ptrType, i32), emptyList(),
                if (is32Bits) {
                    listOf(
                        // ParamGet[1], ParamGet[2], i32Const(storeInstr.numBytes), Call.checkWrite,

                        ParamGet[1], ParamGet[2], I32Add,
                        ParamGet[0], storeInstr, Return
                    )
                } else {
                    listOf(
                        ParamGet[1], ParamGet[2], I64_EXTEND_I32S, I64Add,
                        ParamGet[0], storeInstr, Return
                    )
                }
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
                call.name, listOf(ptrType, i32), listOf(type),
                if (is32Bits) listOf(ParamGet[0], ParamGet[1], I32Add, loadInstr, Return)
                else listOf(ParamGet[0], ParamGet[1], I64_EXTEND_I32S, I64Add, loadInstr, Return)
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

        // a % b = a - a/b*b
        register(
            "f32rem", listOf(f32, f32), listOf(f32),
            listOf(ParamGet[0], ParamGet[0], ParamGet[1], F32Div, ParamGet[1], F32Trunc, F32Mul, F32Sub, Return)
        )
        register(
            "f64rem", listOf(f64, f64), listOf(f64),
            listOf(ParamGet[0], ParamGet[0], ParamGet[1], F64Div, ParamGet[1], F64Trunc, F64Mul, F64Sub, Return)
        )

        fun ifElseChain(
            ifs: List<Pair<List<Instruction>, Instruction>>,
            default: Instruction
        ): List<Instruction> {
            return if (ifs.isEmpty()) {
                listOf(default, Return)
            } else {
                val (firstCondition, firstResult) = ifs.first()
                firstCondition + IfBranch(
                    arrayListOf(firstResult, Return),
                    ArrayList(ifElseChain(ifs.subList(1, ifs.size), default)),
                )
            }
        }

        for ((prefix, row) in listOf(
            "f" to listOf(F32EQ, F32LT, F32GT),
            "d" to listOf(F64EQ, F64LT, F64GT)
        )) {
            val (eq, lt, gt) = row
            val type = if (prefix == "f") "float" else "double"
            val x = ParamGet[0]
            val y = ParamGet[1]
            register( // return -1 if NaN
                "${prefix}cmpl", listOf(type, type), listOf("int"), ifElseChain(
                    listOf(
                        listOf(x, y, eq) to i32Const0, // == -> 0
                        listOf(x, y, gt) to i32Const1 //   > -> 1
                    ), i32ConstM1
                ) + Unreachable
            )
            register( // return +1 if NaN
                "${prefix}cmpg", listOf(type, type), listOf("int"), ifElseChain(
                    listOf(
                        listOf(x, y, eq) to i32Const0, // == ->  0
                        listOf(x, y, lt) to i32ConstM1 //  < -> -1
                    ), i32Const1
                ) + Unreachable
            )
        }

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
