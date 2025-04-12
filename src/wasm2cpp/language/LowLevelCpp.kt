package wasm2cpp.language

import me.anno.utils.assertions.assertTrue
import utils.StringBuilder2
import utils.WASMTypes.i32
import wasm.instr.*
import wasm2cpp.StackToDeclarative.Companion.canAppendWithoutBrackets
import wasm2cpp.StackToDeclarative.Companion.isNumber
import wasm2cpp.expr.*

class LowLevelCpp(val dst: StringBuilder2) : TargetLanguage {

    override fun appendExpr(expr: Expr) {
        when (expr) {
            is ConstExpr -> toConstString(expr)
            is CallExpr -> toCallString(expr)
            is UnaryExpr -> toUnaryString(expr)
            is BinaryExpr -> toBinaryString(expr)
            is VariableExpr -> dst.append(expr.name)
            is FieldGetExpr -> {
                appendExprSafely(expr.instance)
                dst.append('.').append(expr.field.name)
            }
            else -> throw NotImplementedError(expr.javaClass.toString())
        }
    }

    override fun appendExprSafely(expr: Expr) {
        val needsBrackets = when (expr) {
            is VariableExpr,
            is ConstExpr,
            is CallExpr -> false
            else -> true
        }
        if (needsBrackets) dst.append('(')
        appendExpr(expr)
        if (needsBrackets) dst.append(')')
    }

    private fun toConstString(expr: ConstExpr) {
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

    private fun toCallString(expr: CallExpr) {
        dst.append(expr.funcName).append('(')
        val popped = expr.params
        for (i in popped.indices) {
            if (i > 0) dst.append(", ")
            appendExpr(popped[i])
        }
        dst.append(')')
    }

    private fun toUnaryString(expr: UnaryExpr) {
        when (expr.instr) {
            is EqualsZeroInstruction -> {
                if (expr.type == "boolean") {
                    dst.append("!")
                    appendExprSafely(expr.input)
                } else {
                    appendExprSafely(expr)
                    dst.append(" == 0")
                }
            }
            is UnaryFloatInstruction -> {
                val i = expr.instr
                dst.append(i.call).append('(')
                appendExprSafely(expr.input)
                dst.append(')')
            }
            is NumberCastInstruction -> {
                val i = expr.instr
                dst.append(i.prefix)
                appendExprSafely(expr.input)
                dst.append(i.suffix)
            }
            else -> throw NotImplementedError(expr.instr.toString())
        }
    }

    private fun toBinaryString(expr: BinaryExpr) {
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
            is CompareInstr -> {
                // prevent Yoda-speach: if the first is a number, but the second isn't, swap them around
                if (isNumber(i0) && !isNumber(i1)) {
                    // flipped
                    if (i.castType != null) dst.append('(').append(i.castType).append(") ")
                    appendExprSafely(i1)
                    dst.append(' ').append(i.flipped).append(' ')
                    if (i.castType != null) dst.append('(').append(i.castType).append(") ")
                    appendExprSafely(i0)
                } else {
                    if (i.castType != null) dst.append('(').append(i.castType).append(") ")
                    if (canAppendWithoutBrackets(i0, i.operator, true)) appendExpr(i0)
                    else appendExprSafely(i0)
                    dst.append(' ').append(i.operator).append(' ')
                    if (i.castType != null) dst.append('(').append(i.castType).append(") ")
                    if (canAppendWithoutBrackets(i1, i.operator, false)) appendExpr(i1)
                    else appendExprSafely(i1)
                }
            }
            is BinaryInstruction -> {
                if (i.cppOperator.endsWith("(")) {
                    if (i.cppOperator.startsWith("std::rot")) {
                        dst.append(i.cppOperator)
                            .append(if (i.popType == i32) "(u32) " else "(u64) ") // cast to unsigned required
                        appendExprSafely(i0)
                        dst.append(", ")
                        appendExpr(i1)
                        dst.append(')')
                    } else {
                        dst.append(i.cppOperator) // call(i1, i0)
                        appendExpr(i0)
                        dst.append(", ")
                        appendExpr(i1)
                        dst.append(')')
                    }
                } else {
                    if (canAppendWithoutBrackets(i0, i.cppOperator, true)) appendExpr(i0)
                    else appendExprSafely(i0)
                    dst.append(' ').append(i.cppOperator).append(' ')
                    if (canAppendWithoutBrackets(i1, i.cppOperator, false)) appendExpr(i1)
                    else appendExprSafely(i1)
                }
            }
            else -> throw NotImplementedError(i.toString())
        }
    }
}