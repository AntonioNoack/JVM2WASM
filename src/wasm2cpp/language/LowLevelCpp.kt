package wasm2cpp.language

import hIndex
import highlevel.FieldSetInstr
import jvm.JVMFlags.is32Bits
import me.anno.utils.assertions.assertTrue
import translator.LoadStoreHelper.getLoadCall
import translator.LoadStoreHelper.getStaticLoadCall
import translator.LoadStoreHelper.getStaticStoreCall
import translator.LoadStoreHelper.getStoreCall
import utils.*
import utils.WASMTypes.i32
import wasm.instr.*
import wasm.parser.FunctionImpl
import wasm2cpp.FunctionWriter
import wasm2cpp.StackToDeclarative.Companion.canAppendWithoutBrackets
import wasm2cpp.StackToDeclarative.Companion.isNumber
import wasm2cpp.expr.*
import wasm2cpp.instr.*

open class LowLevelCpp(val dst: StringBuilder2) : TargetLanguage {

    companion object {
        val cppKeywords = (
                "alignas,alignof,and,and_eq,asm,atomic_cancel,atomic_commit,atomic_noexcept,auto,bitand,bitor,bool,break," +
                        "case,catch,char,char8_t,char16_t,char32_t,class,compl,concept,const,consteval,constexpr,constinit," +
                        "const_cast,continue,contract_assert,co_await,co_return,co_yield,,decltype,default,delete,do," +
                        "double,dynamic_cast,else,enum,explicit,export,extern,false,float,for,friend,goto,if,inline,int," +
                        "long,mutable,namespace,new,noexcept,not,not_eq,nullptr,operator,or,or_eq,private,protected,public," +
                        "reflexpr,register,reinterpret_cast,requires,return,short,signed,sizeof,static,static_assert," +
                        "static_cast,struct,switch,synchronized,template,this,thread_local,throw,true,try,typedef," +
                        "typeid,typename,union,unsigned,using,virtual,void,volatile,wchar_t,while,xor,xor_eq," +
                        // reserved by default imports :/
                        "OVERFLOW,_OVERFLOW,UNDERFLOW,_UNDERFLOW,NULL"
                ).split(',').toHashSet()

        private val usz = if (is32Bits) "(u32)" else "(u64)"

    }

    override fun appendName(name: String) {
        dst.append(name)
    }

    override fun writeFunctionStart(function: FunctionImpl, writer: FunctionWriter) {
        wasm2cpp.defineFunctionHead(function.funcName, function.params, function.results, true)
        dst.append(" {\n")
    }

    override fun writeStaticInitCheck(writer: FunctionWriter) {
        writer.begin().append("static bool wasCalled = false;\n")
        writer.begin().append(
            if (writer.function.results.isEmpty()) "if(wasCalled) return;\n"
            else "if(wasCalled) return 0;\n"
        )
        writer.begin().append("wasCalled = true;\n")
    }

    override fun writeStaticInstance(className: String) {
        dst.append(className.escapeChars())
    }

    override fun writeGoto(instr: GotoInstr) {
        dst.append("goto ").append(instr.label)
    }

    override fun appendExpr(expr: Expr) {
        when (expr) {
            is ConstExpr -> appendConstExpr(expr)
            is CallExpr -> appendCallExpr(expr)
            is UnaryExpr -> appendUnaryExpr(expr)
            is BinaryExpr -> appendBinaryExpr(expr)
            is VariableExpr -> appendName(expr.name)
            is FieldGetExpr -> appendFieldGetExpr(expr)
            else -> throw NotImplementedError(expr.javaClass.toString())
        }
    }

    open fun appendFieldGetExpr(expr: FieldGetExpr) {
        if (expr.isResultField) {
            appendExpr(expr.instance as VariableExpr)
            dst.append('.')
            dst.append(expr.field.name)
        } else {
            val offsetExpr = getOffsetExpr(expr.field)
            if (expr.field.isStatic) {
                val call = getStaticLoadCall(expr.jvmType)
                appendCallExpr(CallExpr(call.name, listOf(offsetExpr), expr.jvmType))
            } else {
                val call = getLoadCall(expr.jvmType)
                appendCallExpr(CallExpr(call.name, listOf(expr.instance!!, offsetExpr), expr.jvmType))
            }
        }
    }

    override fun writeFieldAssignment(assignment: FieldAssignment, writer: FunctionWriter) {
        writer.begin()
        val offsetExpr = getOffsetExpr(assignment.field)
        val valueExpr = assignment.newValue.expr
        if (assignment.field.isStatic) {
            val call = getStaticStoreCall(assignment.jvmType)
            appendCallExpr(CallExpr(call.name, listOf(valueExpr, offsetExpr), assignment.jvmType))
        } else {
            val call = getStoreCall(assignment.jvmType)
            val instanceExpr = assignment.instance!!.expr
            appendCallExpr(CallExpr(call.name, listOf(instanceExpr, valueExpr, offsetExpr), assignment.jvmType))
        }
        dst.append(";\n")
    }

    private fun getOffsetExpr(field: FieldSig): ConstExpr {
        val isStatic = field.isStatic
        val offset = FieldSetInstr.getFieldAddr(if (isStatic) null else 0, field)
        return ConstExpr(offset, "int")
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

    open fun appendConstExpr(expr: ConstExpr) {
        when (expr.jvmType) {
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

    open fun appendCallExpr(expr: CallExpr) {
        dst.append(expr.funcName).append('(')
        val popped = expr.params
        for (i in popped.indices) {
            if (i > 0) dst.append(", ")
            appendExpr(popped[i])
        }
        dst.append(')')
    }

    open fun appendUnaryExpr(expr: UnaryExpr) {
        when (val i = expr.instr) {
            is EqualsZeroInstruction -> {
                if (expr.jvmType == "boolean") {
                    dst.append("!")
                    appendExprSafely(expr.input)
                } else {
                    appendExprSafely(expr)
                    dst.append(" == 0")
                }
            }
            is UnaryFloatInstruction -> {
                if (i.operator != UnaryOperator.NEGATE) dst.append("std::")
                dst.append(i.operator.symbol).append('(')
                appendExpr(expr.input)
                dst.append(')')
            }
            is NumberCastInstruction -> appendNumberCastExpr(expr, i)
            else -> throw NotImplementedError(expr.instr.toString())
        }
    }

    open fun appendNumberCastExpr(expr: UnaryExpr, instr: NumberCastInstruction) {
        val prefix = getNumberCastPrefix(instr)
        dst.append(prefix)
        appendExprSafely(expr.input)
        val remainingBrackets = prefix.count { it == '(' } - prefix.count { it == ')' }
        for (i in 0 until remainingBrackets) {
            dst.append(')')
        }
    }

    private fun getNumberCastPrefix(i: NumberCastInstruction): String {
        return when (i) {
            Instructions.I32_TRUNC_F32S, Instructions.I32_TRUNC_F64S -> "static_cast<i32>(std::trunc("
            Instructions.I64_TRUNC_F32S, Instructions.I64_TRUNC_F64S -> "static_cast<i64>(std::trunc("
            Instructions.F64_PROMOTE_F32 -> "static_cast<f64>("
            Instructions.F32_DEMOTE_F64 -> "static_cast<f32>("
            Instructions.I64_EXTEND_I32S -> "static_cast<i64>("
            Instructions.I64_EXTEND_I32U -> "static_cast<u64>((u32)("
            Instructions.I32_WRAP_I64 -> "static_cast<i32>("
            Instructions.F32_CONVERT_I32S, Instructions.F32_CONVERT_I64S -> "static_cast<f32>("
            Instructions.F64_CONVERT_I32S, Instructions.F64_CONVERT_I64S -> "static_cast<f64>("
            Instructions.F32_CONVERT_I32U, Instructions.F32_CONVERT_I64U -> "static_cast<f32>((u32)("
            Instructions.F64_CONVERT_I32U, Instructions.F64_CONVERT_I64U -> "static_cast<f64>((u64)("
            Instructions.I32_REINTERPRET_F32 -> "std::bit_cast<i32>("
            Instructions.F32_REINTERPRET_I32 -> "std::bit_cast<f32>("
            Instructions.I64_REINTERPRET_F64 -> "std::bit_cast<i64>("
            Instructions.F64_REINTERPRET_I64 -> "std::bit_cast<f64>("
            else -> throw NotImplementedError()
        }
    }

    open fun appendBinaryExpr(expr: BinaryExpr) {
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
                    dst.append(' ').append(i.flipped.symbol).append(' ')
                    if (i.castType != null) dst.append('(').append(i.castType).append(") ")
                    appendExprSafely(i0)
                } else {
                    if (i.castType != null) dst.append('(').append(i.castType).append(") ")
                    if (i.castType == null && canAppendWithoutBrackets(i0, i.operator, true)) appendExpr(i0)
                    else appendExprSafely(i0)
                    dst.append(' ').append(i.operator.symbol).append(' ')
                    if (i.castType != null) dst.append('(').append(i.castType).append(") ")
                    if (i.castType == null && canAppendWithoutBrackets(i1, i.operator, false)) appendExpr(i1)
                    else appendExprSafely(i1)
                }
            }
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

    override fun beginDeclaration(name: String, jvmType: String) {
        // todo this should be passed along explicitly
        val isCustomType = jvmType !in hIndex.classFlags &&
                jvmType !in NativeTypes.nativeTypes &&
                !jvmType.startsWith("[") && jvmType != "null"
        val wasmType = if (isCustomType) jvmType else jvm2wasmTyped(jvmType).wasmName
        dst.append(wasmType).append(' ')
        appendName(name)
        dst.append(" = ")
    }

    override fun writeFunctionTypeDefinition(instr: FunctionTypeDefinition, writer: FunctionWriter) {
        val type = instr.funcType
        val tmpType = instr.typeName
        val tmpVar = instr.instanceName
        // using CalculateFunc = int32_t(*)(int32_t, int32_t, float);
        // CalculateFunc calculateFunc = reinterpret_cast<CalculateFunc>(funcPtr);
        writer.begin()
            .append("using ").append(tmpType).append(" = ")
        if (type.results.isEmpty()) {
            dst.append("void")
        } else {
            for (ri in type.results.indices) {
                dst.append(type.results[ri])
            }
        }
        dst.append("(*)(")
        for (pi in type.params.indices) {
            if (pi > 0) dst.append(", ")
            dst.append(type.params[pi])
        }
        dst.append(");\n")
        writer.begin()
            .append(tmpType).append(' ').append(tmpVar).append(" = reinterpret_cast<")
            .append(tmpType).append(">(indirect[")
        appendExpr(instr.indexExpr.expr)
        dst.append("]);\n")
    }

    override fun writeLoadInstr(instr: CppLoadInstr, writer: FunctionWriter) {
        writer.begin().append(instr.type).append(' ').append(instr.newName).append(" = ")
            .append("((").append(instr.memoryType).append("*) ((uint8_t*) memory + ").append(usz)
        appendExprSafely(instr.addrExpr.expr)
        dst.append("))[0];\n")
    }

    override fun writeStoreInstr(instr: CppStoreInstr, writer: FunctionWriter) {
        writer.begin().append("((").append(instr.memoryType).append("*) ((uint8_t*) memory + ").append(usz)
        appendExprSafely(instr.addrExpr.expr)
        dst.append("))[0] = ")
        if (instr.type != instr.memoryType) {
            dst.append('(').append(instr.memoryType).append(") ")
        }
        appendExpr(instr.valueExpr.expr)
        dst.append(";\n")
    }
}