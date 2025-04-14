package wasm2cpp.language

import gIndex
import me.anno.utils.Color.hex16
import me.anno.utils.assertions.assertTrue
import utils.NativeTypes
import utils.StringBuilder2
import wasm.instr.*
import wasm.parser.FunctionImpl
import wasm2cpp.FunctionWriter
import wasm2cpp.StackToDeclarative.Companion.canAppendWithoutBrackets
import wasm2cpp.StackToDeclarative.Companion.isNumber
import wasm2cpp.expr.*
import wasm2cpp.instr.*
import wasm2js.classNameToJS
import kotlin.streams.toList

class HighLevelJavaScript(dst: StringBuilder2) : LowLevelCpp(dst) {

    companion object {
        private val allowedCodepoints = " .,-#+%!?:;§&/()[]{}=*^".codePoints().toList()

        val jsKeywords = "function,var,let,const".split(',').toHashSet()
    }

    private var thisVariable: String? = null

    override fun appendName(name: String) {
        dst.append(if (name == thisVariable) "this" else name)
    }

    override fun writeFunctionStart(function: FunctionImpl, writer: FunctionWriter) {
        if (writer.isStatic) dst.append("static ")
        dst.append(function.funcName).append("(")
        // if not static, skip first parameter, and replace it with "this"
        val params = function.params
        assertTrue(writer.isStatic || params.isNotEmpty()) {
            "${function.funcName} isn't static, but also doesn't have params, $params"
        }
        for (i in params.indices) {
            if (i == 0 && !writer.isStatic) continue
            val param = params[i]
            if (!dst.endsWith("(")) dst.append(", ")
            dst.append(param.name)
            dst.append(" /* ").append(param.jvmType).append(" */")
        }
        dst.append(") {\n")
        thisVariable = if (!writer.isStatic) params[0].name else null
    }

    override fun writeStaticInitCheck(writer: FunctionWriter) {
        val className = classNameToJS(writer.className)
        writer.begin().append("").append(className).append(".static_V = DO_NOTHING;\n")
    }

    override fun writeGoto(instr: GotoInstr) {
        // todo make sure that instr.owner is a Loop
        dst.append("continue ").append(instr.label)
    }

    override fun appendConstExpr(expr: ConstExpr) {
        when (expr.jvmType) {
            "i64", "long" -> dst.append(expr.value).append("n")
            "boolean" -> dst.append(
                when (expr.value) {
                    0 -> "false"
                    1 -> "true"
                    else -> throw IllegalArgumentException(expr.toString())
                }
            )
            "f32", "f64", "i32",
            in NativeTypes.nativeTypes -> dst.append(expr.value) // bytes, shorts, chars, ints, floats, doubles, ...
            "java/lang/String" -> {
                if (expr.value == 0 || expr.value == 0L) {
                    dst.append("null")
                } else {
                    val stringValue = expr.value as String
                    // todo escape string as necessary
                    dst.append("wrapString(\"")
                    for (codepoint in stringValue.codePoints()) {
                        when (codepoint) {
                            in '0'.code..'9'.code,
                            in 'A'.code..'Z'.code,
                            in 'a'.code..'z'.code,
                            in allowedCodepoints -> {
                                dst.append(codepoint.toChar())
                            }
                            else -> dst.append("\\u").append(hex16(codepoint))
                        }
                    }
                    dst.append("\")")
                }
            }
            else -> {
                assertTrue(expr.value == 0 || expr.value == 0L) {
                    "Weird constant: ${expr.value} (${expr.value.javaClass})"
                }
                dst.append("null")
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
            Instructions.F32_CONVERT_I64U, Instructions.F64_CONVERT_I64U -> dst.append(" & 0xFFFFFFFFFFFFFFFFn)")
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
                appendExprSafely(i0)
                dst.append(
                    when (i.operator) {
                        BinaryOperator.SHIFT_LEFT -> " << "
                        BinaryOperator.SHIFT_RIGHT_SIGNED -> " >> "
                        BinaryOperator.SHIFT_RIGHT_UNSIGNED -> " >>> "
                        else -> throw NotImplementedError()
                    }
                )
                appendExprSafely(i1)
            }
            is CompareInstr -> {
                val castType = when (i.castType) {
                    "u32" -> " >>> 0"
                    "u64" -> " & 0xffffffffffffffffn"
                    null -> null
                    else -> throw NotImplementedError()
                }
                // prevent Yoda-speach: if the first is a number, but the second isn't, swap them around
                if (isNumber(i0) && !isNumber(i1)) {
                    // flipped
                    appendExprSafely(i1)
                    if (castType != null) dst.append(castType)
                    dst.append(' ').append(i.flipped.symbol).append(' ')
                    appendExprSafely(i0)
                    if (castType != null) dst.append(castType)
                } else {
                    if (castType == null && canAppendWithoutBrackets(i0, i.operator, true)) appendExpr(i0)
                    else appendExprSafely(i0)
                    if (castType != null) dst.append(castType)
                    dst.append(' ').append(i.operator.symbol).append(' ')
                    if (castType == null && canAppendWithoutBrackets(i1, i.operator, false)) appendExpr(i1)
                    else appendExprSafely(i1)
                    if (castType != null) dst.append(castType)
                }
            }
            is BinaryInstruction -> {
                when (i.operator) {
                    BinaryOperator.ADD, BinaryOperator.SUB,
                    BinaryOperator.MULTIPLY, BinaryOperator.DIVIDE, BinaryOperator.REMAINDER,
                    BinaryOperator.AND, BinaryOperator.OR, BinaryOperator.XOR -> {
                        if (canAppendWithoutBrackets(i0, i.operator, true)) appendExpr(i0)
                        else appendExprSafely(i0)
                        dst.append(' ').append(i.operator.symbol).append(' ')
                        if (canAppendWithoutBrackets(i1, i.operator, false)) appendExpr(i1)
                        else appendExprSafely(i1)
                    }
                    else -> {
                        dst.append("Math.").append(i.operator.symbol).append('(')
                        if (i.operator.symbol.startsWith("rot")) {
                            // todo implement rotation somehow...
                            appendExprSafely(i0)
                        } else {
                            appendExpr(i0)
                        }
                        dst.append(", ")
                        appendExpr(i1)
                        dst.append(')')
                    }
                }
            }
            else -> throw NotImplementedError(i.toString())
        }
    }

    override fun beginDeclaration(name: String, jvmType: String) {
        dst.append("let ")
        appendName(name)
        dst.append(" /* ").append(jvmType).append(" */ = ")
    }

    override fun writeFunctionTypeDefinition(instr: FunctionTypeDefinition, writer: FunctionWriter) {
        writer.begin()
            .append("const ").append(instr.instanceName).append(" = indirectFunctions[")
        appendExpr(instr.indexExpr.expr)
        dst.append("];\n")
    }

    override fun appendCallExpr(expr: CallExpr) {
        if (expr.funcName == "createInstance" && expr.params.size == 1 && expr.params[0] is ConstExpr) {
            val classId = (expr.params[0] as ConstExpr).value as Int
            dst.append("new ").append(classNameToJS(gIndex.classNames[classId])).append("()")
        } else if (expr.funcName == "getClassIdPtr" && expr.params.size == 1 && expr.params[0] is ConstExpr) {
            val classId = (expr.params[0] as ConstExpr).value as Int
            dst.append(classNameToJS(gIndex.classNames[classId])).append(".LAMBDA_INSTANCE")
        } else if (expr.funcName == "findClass" && expr.params.size == 1 && expr.params[0] is ConstExpr) {
            val classId = (expr.params[0] as ConstExpr).value as Int
            dst.append(classNameToJS(gIndex.classNames[classId])).append(".CLASS_INSTANCE")
        } else {
            // todo if is not static, use first argument as caller
            // todo shorten name
            dst.append(expr.funcName).append('(')
            val popped = expr.params
            for (i in popped.indices) {
                if (i > 0) dst.append(", ")
                appendExpr(popped[i])
            }
            dst.append(')')
        }
    }

    override fun appendFieldGetExpr(expr: FieldGetExpr) {
        if (expr.instance != null) {
            appendExprSafely(expr.instance)
        } else {
            writeStaticInstance(expr.field.clazz)
        }
        dst.append('.')
        dst.append(expr.field.name)
    }

    override fun writeFieldAssignment(assignment: FieldAssignment, writer: FunctionWriter) {
        writer.begin()
        if (assignment.instance != null) appendExpr(assignment.instance.expr)
        else writeStaticInstance(assignment.field.clazz)
        dst.append('.').append(assignment.field.name).append(" = ")
        appendExpr(assignment.newValue.expr)
        dst.append(";\n")
    }

    override fun writeLoadInstr(instr: CppLoadInstr, writer: FunctionWriter) {
        writer.begin().append("throw new Error('loadInstr not supported');\n")
    }

    override fun writeStoreInstr(instr: CppStoreInstr, writer: FunctionWriter) {
        writer.begin().append("throw new Error('storeInstr not supported');\n")
    }
}