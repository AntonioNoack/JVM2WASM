package wasm2cpp.language

import me.anno.utils.assertions.assertTrue
import utils.StringBuilder2
import utils.WASMTypes.i32
import wasm.instr.*
import wasm.parser.FunctionImpl
import wasm2cpp.FunctionWriter
import wasm2cpp.StackToDeclarative.Companion.canAppendWithoutBrackets
import wasm2cpp.expr.BinaryExpr
import wasm2cpp.expr.ConstExpr
import wasm2cpp.expr.UnaryExpr
import wasm2cpp.instr.FunctionTypeDefinition
import wasm2cpp.writer

class LowLevelJavaScript(dst: StringBuilder2) : LowLevelCpp(dst) {

    override fun defineFunctionHead(function: FunctionImpl, needsParameterNames: Boolean) {
        writer.append("function ").append(function.funcName).append("(")
        val params = function.params
        for (i in params.indices) {
            val param = params[i]
            if (i > 0) writer.append(", ")
            writer.append(param.name)
            writer.append(" /* ").append(param.jvmType).append(" */")
        }
        writer.append(")")
    }

    override fun appendConstExpr(expr: ConstExpr) {
        when (expr.type) {
            "f32", "float" -> dst.append(expr.value).append('f')
            "f64", "double" -> dst.append(expr.value)
            "i32", "int" -> {
                if (expr.value == Int.MIN_VALUE) dst.append("(i32)(1u << 31)")
                else dst.append(expr.value)
            }
            "i64", "long" -> {
                if (expr.value == Long.MIN_VALUE) dst.append("(i64)(1llu << 63)")
                else dst.append(expr.value).append("ll")
            }
            else -> {
                assertTrue(expr.value == 0 || expr.value == 0L) {
                    "Weird constant: ${expr.value} (${expr.value.javaClass})"
                }
                dst.append("0")
            }
        }
    }

    override fun appendUnaryExpr(expr: UnaryExpr) {
        when (val i = expr.instr) {
            is EqualsZeroInstruction -> {
                dst.append("!")
                appendExprSafely(expr.input)
            }
            is UnaryFloatInstruction -> {
                if (i.operator != UnaryOperator.NEGATE) dst.append("Math.")
                dst.append(i.operator.symbol).append('(')
                appendExpr(expr.input)
                dst.append(')')
            }
            is NumberCastInstruction -> appendNumberCastExpr(expr, i)
            else -> throw NotImplementedError(expr.instr.toString())
        }
    }

    override fun appendNumberCastExpr(expr: UnaryExpr, instr: NumberCastInstruction) {
        val prefix = getNumberCastPrefix(instr)
        dst.append(prefix)
        appendExprSafely(expr.input)
        appendNumberCastSuffix(instr, prefix)

    }

    private fun getNumberCastPrefix(i: NumberCastInstruction): String {
        // how do we work with 64-bit integers in JavaScript??? -> BigInt
        /*
        * BigInt.asIntN()
    Clamps a BigInt value to a signed integer value, and returns that value.

    BigInt.asUintN()
    Clamps a BigInt value to an unsigned integer value, and returns that value.*/

        return when (i) {
            Instructions.I32_TRUNC_F32S, Instructions.I32_TRUNC_F64S -> "Math.trunc("
            Instructions.I64_TRUNC_F32S, Instructions.I64_TRUNC_F64S -> "BigInt("
            Instructions.F64_PROMOTE_F32, Instructions.F32_DEMOTE_F64 -> "" // done automatically
            Instructions.F32_CONVERT_I32S, Instructions.F64_CONVERT_I32S -> "" // done automatically
            Instructions.I64_EXTEND_I32S -> "BigInt("
            Instructions.I64_EXTEND_I32U -> "BigInt(" // needs >>> 0 to make it unsigned
            Instructions.I32_WRAP_I64 -> "Number(" // needs & 0xFFFFFFFFn) | 0 to extract those bits and make it signed
            Instructions.F32_CONVERT_I64S, Instructions.F64_CONVERT_I64S -> "Number("
            Instructions.F32_CONVERT_I32U, Instructions.F64_CONVERT_I32U -> "" // needs >>> 0 to make it unsigned
            Instructions.F32_CONVERT_I64U -> "Number(" // needs & 0xFFFFFFFFn)
            Instructions.F64_CONVERT_I64U -> "Number(" // needs & 0xFFFFFFFFFFFFFFFFn)
            // todo we need to implement the proper methods here...
            Instructions.I32_REINTERPRET_F32 -> "bit_cast<i32>("
            Instructions.F32_REINTERPRET_I32 -> "bit_cast<f32>("
            Instructions.I64_REINTERPRET_F64 -> "bit_cast<i64>("
            Instructions.F64_REINTERPRET_I64 -> "bit_cast<f64>("
            else -> throw NotImplementedError()
        }
    }

    private fun appendNumberCastSuffix(instr: NumberCastInstruction, prefix: String) {
        when (instr) {
            Instructions.I64_EXTEND_I32U -> dst.append(" >>> 0)")
            Instructions.I32_WRAP_I64 -> dst.append(" & 0xFFFFFFFFn) | 0")
            Instructions.F32_CONVERT_I32U, Instructions.F64_CONVERT_I32U -> dst.append(" >>> 0")
            Instructions.F32_CONVERT_I64U -> dst.append(" & 0xFFFFFFFFn)")
            Instructions.F64_CONVERT_I64U -> dst.append(" & 0xFFFFFFFFFFFFFFFFn)")
            else -> {
                val remainingBrackets = prefix.count { it == '(' } - prefix.count { it == ')' }
                for (i in 0 until remainingBrackets) {
                    dst.append(')')
                }
            }
        }
    }

    override fun appendBinaryExpr(expr: BinaryExpr) {
        val i0 = expr.compA
        val i1 = expr.compB
        when (val i = expr.instr) {
            is ShiftInstr -> {
                val needsCast = i.isRight && i.isUnsigned
                if (needsCast) {
                    dst.append(if (i.popType == i32) "(i32)((u32) " else "(i64)((u64) ")
                }
                appendExprSafely(i0)
                dst.append(if (i.isRight) " >> " else " << ")
                appendExprSafely(i1)
                if (needsCast) {
                    dst.append(')')
                }
            }
            is CompareInstr -> super.appendBinaryExpr(expr)
            is BinaryInstruction -> {
                if (i.operator.symbol.length > 1) {
                    dst.append("std::").append(i.operator.symbol).append('(')
                    if (i.operator.symbol.startsWith("rot")) {
                        dst.append(if (i.popType == i32) "(u32) " else "(u64) ") // cast to unsigned required
                        appendExprSafely(i0)
                    } else {
                        appendExpr(i0)
                    }
                    dst.append(", ")
                    appendExpr(i1)
                    dst.append(')')
                } else {
                    if (canAppendWithoutBrackets(i0, i.operator, true)) appendExpr(i0)
                    else appendExprSafely(i0)
                    dst.append(' ').append(i.operator.symbol).append(' ')
                    if (canAppendWithoutBrackets(i1, i.operator, false)) appendExpr(i1)
                    else appendExprSafely(i1)
                }
            }
            else -> throw NotImplementedError(i.toString())
        }
    }

    override fun beginDeclaration(name: String, type: String) {
        dst.append("let ").append(name).append(" /* ").append(type).append(" */ = ")
    }

    override fun writeFunctionTypeDefinition(instr: FunctionTypeDefinition, writer: FunctionWriter) {
        writer.begin()
            .append("const ").append(instr.instanceName).append(" = indirectFunctions[")
        appendExpr(instr.indexExpr.expr)
        dst.append("];\n")
    }
}