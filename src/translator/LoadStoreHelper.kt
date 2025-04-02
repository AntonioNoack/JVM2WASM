package translator

import utils.is32Bits
import wasm.instr.Call
import wasm.instr.Instructions.F32Load
import wasm.instr.Instructions.F32Store
import wasm.instr.Instructions.F64Load
import wasm.instr.Instructions.F64Store
import wasm.instr.Instructions.I32Load
import wasm.instr.Instructions.I32Load16S
import wasm.instr.Instructions.I32Load16U
import wasm.instr.Instructions.I32Load8S
import wasm.instr.Instructions.I32Store
import wasm.instr.Instructions.I32Store16
import wasm.instr.Instructions.I32Store8
import wasm.instr.Instructions.I64Load
import wasm.instr.Instructions.I64Store
import wasm.instr.LoadInstr
import wasm.instr.StoreInstr

object LoadStoreHelper {

    fun getLoadInstr(descriptor: String): LoadInstr = when (descriptor) {
        "boolean", "byte" -> I32Load8S
        "short" -> I32Load16S
        "char" -> I32Load16U
        "int" -> I32Load
        "long" -> I64Load
        "float" -> F32Load
        "double" -> F64Load
        else -> if (is32Bits) I32Load else I64Load
    }

    fun getStoreInstr(descriptor: String): StoreInstr = when (descriptor) {
        "boolean", "byte" -> I32Store8
        "short", "char" -> I32Store16
        "int" -> I32Store
        "long" -> I64Store
        "float" -> F32Store
        "double" -> F64Store
        "Z", "B", "I", "J", "F", "D" -> throw IllegalArgumentException()
        else -> if (is32Bits) I32Store else I64Store
    }

    fun getStaticLoadCall(descriptor: String): Call = when (descriptor) {
        "boolean", "byte" -> Call.getStaticFieldS8
        "short" -> Call.getStaticFieldS16
        "char" -> Call.getStaticFieldU16
        "int" -> Call.getStaticFieldI32
        "long" -> Call.getStaticFieldI64
        "float" -> Call.getStaticFieldF32
        "double" -> Call.getStaticFieldF64
        "Z", "B", "I", "J", "F", "D" -> throw IllegalArgumentException()
        else -> if (is32Bits) Call.getStaticFieldI32 else Call.getStaticFieldI64
    }

    fun getLoadCall(descriptor: String): Call = when (descriptor) {
        "boolean", "byte" -> Call.getFieldS8
        "short" -> Call.getFieldS16
        "char" -> Call.getFieldU16
        "int" -> Call.getFieldI32
        "long" -> Call.getFieldI64
        "float" -> Call.getFieldF32
        "double" -> Call.getFieldF64
        "Z", "B", "I", "J", "F", "D" -> throw IllegalArgumentException()
        else -> if (is32Bits) Call.getFieldI32 else Call.getFieldI64
    }

    fun getStaticStoreCall(descriptor: String): Call = when (descriptor) {
        "boolean", "byte" -> Call.setStaticFieldI8
        "short", "char" -> Call.setStaticFieldI16
        "int" -> Call.setStaticFieldI32
        "long" -> Call.setStaticFieldI64
        "float" -> Call.setStaticFieldF32
        "double" -> Call.setStaticFieldF64
        "Z", "B", "I", "J", "F", "D" -> throw IllegalArgumentException()
        else -> if (is32Bits) Call.setStaticFieldI32 else Call.setStaticFieldI64
    }

    fun getStoreCall(descriptor: String): Call = when (descriptor) {
        "boolean", "byte" -> Call.setFieldI8
        "short", "char" -> Call.setFieldI16
        "int" -> Call.setFieldI32
        "long" -> Call.setFieldI64
        "float" -> Call.setFieldF32
        "double" -> Call.setFieldF64
        "Z", "B", "I", "J", "F", "D" -> throw IllegalArgumentException()
        else -> if (is32Bits) Call.setFieldI32 else Call.setFieldI64
    }

    fun getVIOStoreCall(descriptor: String): Call = when (descriptor) {
        "boolean", "byte" -> Call.setVIOFieldI8
        "short", "char" -> Call.setVIOFieldI16
        "int" -> Call.setVIOFieldI32
        "long" -> Call.setVIOFieldI64
        "float" -> Call.setVIOFieldF32
        "double" -> Call.setVIOFieldF64
        "Z", "B", "I", "J", "F", "D" -> throw IllegalArgumentException()
        else -> if (is32Bits) Call.setVIOFieldI32 else Call.setVIOFieldI64
    }

}