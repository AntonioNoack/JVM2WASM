package wasm2cpp.language

import checkArrayAccess
import gIndex
import me.anno.utils.Color.hex16
import me.anno.utils.assertions.assertTrue
import utils.FieldSig
import utils.NativeTypes
import utils.StringBuilder2
import utils.jvm2wasmTyped
import wasm.instr.*
import wasm.instr.Instructions.I32Add
import wasm.instr.Instructions.I32Mul
import wasm.instr.Instructions.I32Sub
import wasm.instr.Instructions.I32_DIVS
import wasm.instr.Instructions.I64ShrU
import wasm.parser.FunctionImpl
import wasm2cpp.FunctionWriter
import wasm2cpp.StackToDeclarative.Companion.canAppendWithoutBrackets
import wasm2cpp.StackToDeclarative.Companion.isNumber
import wasm2cpp.expr.*
import wasm2cpp.instr.*
import wasm2js.classNameToJS
import wasm2js.minifyJavaScript
import wasm2js.shortName
import wasm2js.usedFromOutsideClasses
import kotlin.streams.toList

class HighLevelJavaScript(dst: StringBuilder2) : LowLevelCpp(dst) {

    companion object {
        private val allowedCodepoints = " .,_-#+%!?:;§$&/()[]{}=*^".codePoints().toList()

        val jsKeywords = ("" +
                "function,var,let,const,in,if,while,do,else,class,super," +
                "gl," +
                "AB,AZ,AS,AC,AI,AL,AF,AD,AW")
            .split(',').toHashSet()

        val unsafeArrayLoadInstructions = listOf(
            Call.s8ArrayLoadU,
            Call.s16ArrayLoadU,
            Call.u16ArrayLoadU,
            Call.i32ArrayLoadU,
            Call.i64ArrayLoadU,
            Call.f32ArrayLoadU,
            Call.f64ArrayLoadU,
        ).map { it.name }
        val unsafeArrayStoreInstructions = listOf(
            Call.i8ArrayStoreU,
            Call.i16ArrayStoreU,
            Call.i32ArrayStoreU,
            Call.i64ArrayStoreU,
            Call.f32ArrayStoreU,
            Call.f64ArrayStoreU,
        ).map { it.name }

        fun fieldName(sig: FieldSig): String {
            return if (minifyJavaScript && sig.clazz !in usedFromOutsideClasses && false) {
                // todo this is causing crashes :( and only saving 3% space
                shortName(Triple("field", sig.clazz, sig.name))
            } else sig.name
        }
    }

    private var thisVariable: String? = null

    override fun end() {
        dst.append(";\n")
    }

    override fun appendName(name: String) {
        val newName = when {
            name == thisVariable -> "this"
            minifyJavaScript -> shortName(Triple("local", "", name))
            else -> name
        }
        dst.append(newName)
    }

    override fun writeFunctionStart(function: FunctionImpl, writer: FunctionWriter) {
        if (writer.className.isNotEmpty()) {
            if (writer.isStatic) dst.append("static ")
        } else dst.append("function ")
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
            appendName(param.name)
            if (!minifyJavaScript) {
                dst.append(" /* ").append(param.jvmType).append(" */")
            }
        }
        dst.append(") {\n")
        thisVariable = if (!writer.isStatic) params[0].name else null
    }

    override fun writeStaticInitCheck(writer: FunctionWriter) {
        val className = classNameToJS(writer.className)
        writer.begin().append(className)
            .append('.').append(writer.function.funcName)
            .append(if (minifyJavaScript) "=" else " = ")
            .append("DO_NOTHING;\n")
    }

    override fun writeUnreachable(function: FunctionWriter) {
        if (minifyJavaScript) {
            dst.append("unreachable()\n")
        } else super.writeUnreachable(function)
    }

    override fun writeGoto(instr: GotoInstr) {
        // to do make sure that instr.owner is a Loop
        dst.append("continue ")
        appendName(instr.label)
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
                    // escape string as necessary
                    dst.append(if (minifyJavaScript) "$(\"" else "wrapString(\"")
                    for (codepoint in stringValue.codePoints()) {
                        when (codepoint) {
                            in '0'.code..'9'.code,
                            in 'A'.code..'Z'.code,
                            in 'a'.code..'z'.code,
                            in allowedCodepoints -> dst.append(codepoint.toChar())
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
            Instructions.I64_TRUNC_F32S, Instructions.I64_TRUNC_F64S -> "BigInt(Math.trunc("
            Instructions.F64_PROMOTE_F32, Instructions.F32_DEMOTE_F64 -> "" // done automatically
            Instructions.F32_CONVERT_I32S, Instructions.F64_CONVERT_I32S -> "" // done automatically
            Instructions.I64_EXTEND_I32S -> "BigInt("
            Instructions.I64_EXTEND_I32U -> "BigInt(" // needs >>> 0 to make it unsigned
            Instructions.I32_WRAP_I64 -> "Number(" // needs & 0xFFFFFFFFn) | 0 to extract those bits and make it signed
            Instructions.F32_CONVERT_I64S, Instructions.F64_CONVERT_I64S -> "Number("
            Instructions.F32_CONVERT_I32U, Instructions.F64_CONVERT_I32U -> "" // needs >>> 0 to make it unsigned
            Instructions.F32_CONVERT_I64U -> "Number(" // needs & 0xFFFFFFFFn)
            Instructions.F64_CONVERT_I64U -> "Number(" // needs & 0xFFFFFFFFFFFFFFFFn)
            Instructions.I32_REINTERPRET_F32 -> "getF32Bits("
            Instructions.F32_REINTERPRET_I32 -> "fromF32Bits("
            Instructions.I64_REINTERPRET_F64 -> "getF64Bits("
            Instructions.F64_REINTERPRET_I64 -> "fromF64Bits("
            else -> throw NotImplementedError()
        }
    }

    private fun appendNumberCastSuffix(instr: NumberCastInstruction, prefix: String) {
        when (instr) {
            Instructions.I64_EXTEND_I32U -> dst.append(" >>> 0)")
            Instructions.I32_WRAP_I64 -> dst.append(" & 0xFFFFFFFFn) | 0")
            Instructions.F32_CONVERT_I32U, Instructions.F64_CONVERT_I32U -> dst.append(" >>> 0")
            Instructions.F32_CONVERT_I64U, Instructions.F64_CONVERT_I64U -> dst.append(" & 0xFFFFFFFFFFFFFFFFn)")
            Instructions.I32_TRUNC_F32S, Instructions.I32_TRUNC_F64S -> dst.append(")|0")
            Instructions.I64_TRUNC_F32S, Instructions.I64_TRUNC_F64S -> {
                // ||0 is to avoid NaN crashing the function
                // todo clamp to valid range
                dst.append(")||0)")
            }
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
                        BinaryOperator.SHIFT_RIGHT_UNSIGNED -> {
                            // length is unspecified -> unsigned right shift isn't defined
                            if (i == I64ShrU) "& 0xffffffffffffffffn >> "
                            else " >>> "
                        }
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
            I32Mul -> {
                dst.append("Math.imul(")
                appendExpr(i0)
                dst.append(',')
                appendExpr(i1)
                dst.append(')')
            }
            I32Add, I32Sub, I32_DIVS -> {
                i as BinaryI32Instruction
                dst.append("(")
                appendExprSafely(i0)
                // todo we can remove these spaces, when we remove cases of (127--128)
                dst.append(' ').append(i.operator.symbol).append(' ')
                appendExprSafely(i1)
                dst.append(")|0")
            }
            is BinaryInstruction -> {
                when (i.operator) {
                    BinaryOperator.ADD, BinaryOperator.SUB,
                    BinaryOperator.MULTIPLY, BinaryOperator.DIVIDE, BinaryOperator.REMAINDER,
                    BinaryOperator.AND, BinaryOperator.OR, BinaryOperator.XOR -> {
                        // todo be careful with overflows in some of these operations
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
                        dst.append(if (minifyJavaScript) "," else ", ")
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
        if (!minifyJavaScript) {
            dst.append(" /* ").append(jvmType).append(" */ = ")
        } else dst.append('=')
    }

    override fun beginAssignment(name: String) {
        appendName(name)
        dst.append(if (minifyJavaScript) "=" else " = ")
    }

    override fun writeFunctionTypeDefinition(instr: FunctionTypeDefinition, writer: FunctionWriter) {
        writer.begin()
            .append("const ").append(instr.instanceName).append(" = indirectFunctions[")
        appendExpr(instr.indexExpr.expr)
        dst.append("];\n")
    }

    override fun appendCallExpr(expr: CallExpr) {
        val params = expr.params
        if (expr.funcName == "createInstance" && params.size == 1 && params[0] is ConstExpr) {
            val classId = (params[0] as ConstExpr).value as Int
            dst.append("new ").append(classNameToJS(gIndex.classNames[classId])).append("()")
        } else if (expr.funcName == "getClassIdPtr" && params.size == 1 && params[0] is ConstExpr) {
            val classId = (params[0] as ConstExpr).value as Int
            dst.append(classNameToJS(gIndex.classNames[classId])).append(".LAMBDA_INSTANCE")
        } else if (expr.funcName == "findClass" && params.size == 1 && params[0] is ConstExpr) {
            val classId = (params[0] as ConstExpr).value as Int
            dst.append(classNameToJS(gIndex.classNames[classId])).append(".CLASS_INSTANCE")
        } else if (!checkArrayAccess && expr.funcName in unsafeArrayLoadInstructions && params.size == 2) {
            // array.values[index]
            appendExprSafely(params[0])
            dst.append(".values[")
            appendExpr(params[1])
            dst.append(']')
        } else if (!checkArrayAccess && expr.funcName in unsafeArrayStoreInstructions && params.size == 3) {
            // array.values[index] = value
            appendExprSafely(params[0])
            dst.append(".values[")
            appendExpr(params[1])
            dst.append(if (minifyJavaScript) "]=" else "] = ")
            appendExpr(params[2])
        } else {
            dst.append(expr.funcName).append('(')
            for (i in params.indices) {
                if (i > 0) dst.append(if (minifyJavaScript) "," else ", ")
                appendExpr(params[i])
            }
            dst.append(')')
        }
    }

    override fun writeStaticInstance(className: String) {
        dst.append(classNameToJS(className))
    }

    override fun appendFieldGetExpr(expr: FieldGetExpr) {
        if (expr.instance != null) {
            appendExprSafely(expr.instance)
        } else {
            writeStaticInstance(expr.field.clazz)
        }
        dst.append('.')
        dst.append(fieldName(expr.field))
    }

    override fun writeFieldAssignment(assignment: FieldAssignment, writer: FunctionWriter) {
        writer.begin()
        if (assignment.instance != null) appendExpr(assignment.instance.expr)
        else writeStaticInstance(assignment.field.clazz)
        dst.append('.').append(fieldName(assignment.field))
        dst.append(if (minifyJavaScript) "=" else " = ")
        appendExpr(assignment.newValue.expr)
        end()
    }

    override fun writeLoadInstr(instr: CppLoadInstr, writer: FunctionWriter) {
        writer.begin().append("throw new Error('loadInstr not supported');\n")
    }

    override fun writeStoreInstr(instr: CppStoreInstr, writer: FunctionWriter) {
        writer.begin().append("throw new Error('storeInstr not supported');\n")
    }

    override fun writeReturnStruct(results: List<Expr>) {
        val totalType = results.joinToString("") { jvm2wasmTyped(it.jvmType).wasmName }
        dst.append("return { ")
        for (ri in results.indices) {
            if (ri > 0) dst.append(", ")
            val sig = FieldSig(totalType, "v$ri", results[ri].jvmType, false)
            dst.append(fieldName(sig)).append(": ")
            appendExpr(results[ri])
        }
        dst.append(" }")
    }
}