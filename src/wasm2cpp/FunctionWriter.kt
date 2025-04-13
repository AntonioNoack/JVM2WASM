package wasm2cpp

import jvm.JVMFlags.is32Bits
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFail
import me.anno.utils.assertions.assertTrue
import utils.StringBuilder2
import utils.jvm2wasmTyped
import wasm.instr.Comment
import wasm.instr.Instruction
import wasm.instr.Instructions.Unreachable
import wasm.instr.LoopInstr
import wasm.parser.FunctionImpl
import wasm.parser.GlobalVariable
import wasm2cpp.StackToDeclarative.Companion.nextInstr
import wasm2cpp.expr.CallExpr
import wasm2cpp.instr.*
import wasm2cpp.language.TargetLanguage

// todo inherit from this class and...
//  - using HighLevel getters, setters and local-variables, pass around true structs
//  - using that, generate JavaScript
// todo new highLevel instructions for instanceOf and indirect calls

// todo intermediate stack-less representation with assignments and complex expressions

// todo enable all warnings, and clear them all for truly clean code
//  - ignore not-used outputs from functions
//  - mark functions as pure (compile-time constant)
//  - inline pure functions (incl. potential reordering) into expressions
//  - discard unused expressions

class FunctionWriter(val globals: Map<String, GlobalVariable>, val language: TargetLanguage) {

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

        private var debugInstructions = false
    }

    var depth = 0
    lateinit var function: FunctionImpl
    var isStatic = false

    fun write(function: FunctionImpl, isStatic: Boolean) {

        this.function = function
        this.isStatic = isStatic

        begin()
        language.defineFunctionHead(function, this)
        writer.append(" {\n")
        depth++

        if (function.funcName.startsWith("static_")) {
            language.writeStaticInitCheck(this)
        }

        writeInstructions(function.body)

        depth--
        begin().append("}\n")
    }

    fun begin(): StringBuilder2 {
        for (i in 0 until depth) writer.append("  ")
        return writer
    }

    private fun StringBuilder2.end() {
        append(";\n")
    }

    private fun writeInstructions(instructions: List<Instruction>) {
        for (i in instructions.indices) {
            writeInstruction(instructions[i])
        }
    }

    private fun writeInstruction(instr: Instruction) {
        if (debugInstructions) writeDebugInfo(instr)
        when (instr) {
            is CppLoadInstr -> writeLoadInstr(instr)
            is CppStoreInstr -> writeStoreInstr(instr)
            is NullDeclaration -> writeNullDeclaration(instr)
            is Declaration -> writeDeclaration(instr)
            is Assignment -> writeAssignment(instr)
            is FieldAssignment -> writeFieldAssignment(instr)
            is ExprReturn -> writeExprReturn(instr)
            Unreachable -> writeUnreachable()
            is ExprIfBranch -> writeIfBranch(instr, emptyList())
            is ExprCall -> writeExprCall(instr)
            is FunctionTypeDefinition -> language.writeFunctionTypeDefinition(instr, this)
            is LoopInstr -> writeLoopInstr(instr)
            is GotoInstr -> writeGoto(instr)
            is BreakThisLoopInstr -> writeBreakThisLoop()
            is Comment -> writeComment(instr)
            else -> assertFail("Unknown instruction type ${instr.javaClass}")
        }
    }

    private fun writeComment(instr: Comment) {
        assertTrue('\n' !in instr.text)
        begin().append("// ").append(instr.text).append('\n')
    }

    private fun writeGoto(instr: GotoInstr) {
        begin()
        language.writeGoto(instr)
        writer.end()
    }

    private fun writeBreakThisLoop() {
        begin().append("break").end()
    }

    private fun writeDebugInfo(instr: Instruction) {
        begin().append("/* ").append(instr.javaClass.simpleName)
        if (instr is Declaration) writer.append(", ").append(instr.initialValue.dependencies)
        writer.append(" */\n")
    }

    private val usz = if (is32Bits) "(u32)" else "(u64)"

    private fun writeLoadInstr(instr: CppLoadInstr) {
        begin().append(instr.type).append(' ').append(instr.newName).append(" = ")
            .append("((").append(instr.memoryType).append("*) ((uint8_t*) memory + ").append(usz)
        language.appendExprSafely(instr.addrExpr.expr)
        writer.append("))[0]").end()
    }

    private fun writeStoreInstr(instr: CppStoreInstr) {
        begin().append("((").append(instr.memoryType).append("*) ((uint8_t*) memory + ").append(usz)
        language.appendExprSafely(instr.addrExpr.expr)
        writer.append("))[0] = ")
        if (instr.type != instr.memoryType) {
            writer.append('(').append(instr.memoryType).append(") ")
        }
        language.appendExpr(instr.valueExpr.expr)
        writer.end()
    }

    private fun writeNullDeclaration(instr: NullDeclaration) {
        begin()
        language.beginDeclaration(instr.name, instr.jvmType)
        writer.append("0").end()
    }

    private fun writeDeclaration(instr: Declaration) {
        begin()
        language.beginDeclaration(instr.name, instr.jvmType)
        language.appendExpr(instr.initialValue.expr)
        writer.end()
    }

    private fun writeAssignment(assignment: Assignment) {
        begin().append(assignment.name).append(" = ")
        language.appendExpr(assignment.newValue.expr)
        writer.end()
    }

    private fun writeFieldAssignment(assignment: FieldAssignment) {
        language.writeFieldAssignment(assignment, this)
    }

    private fun writeExprReturn(instr: ExprReturn) {
        val results = instr.results
        assertEquals(function.results.size, results.size)
        when (results.size) {
            0 -> begin().append("return").end()
            1 -> {
                begin().append("return ")
                language.appendExpr(results.first().expr)
                writer.end()
            }
            else -> {
                begin().append("return { ")
                for (ri in results.indices) {
                    if (ri > 0) writer.append(", ")
                    language.appendExpr(results[ri].expr)
                }
                writer.append(" }").end()
            }
        }
    }

    private fun writeUnreachable() {
        begin().append("unreachable(\"")
            .append(function.funcName).append("\")").end()
    }

    private fun writeExprCall(instr: ExprCall) {

        begin()

        if (instr.isReturn) {
            writer.append("return ")
        } else if (instr.resultName != null) {
            if (instr.resultType != null) {
                language.beginDeclaration(instr.resultName, instr.resultType)
            } else {
                writer.append(instr.resultName).append(" = ")
            }
        }

        language.appendExpr(CallExpr(instr.funcName, instr.params.map { it.expr }, instr.resultType ?: "?"))
        writer.end()
    }

    private fun writeLoopInstr(instr: LoopInstr) {
        begin().append(instr.label).append(": while (true) {\n")
        depth++
        writeInstructions(instr.body)
        depth--
        begin().append("}\n")
    }

    private fun writeIfBranch(instr: ExprIfBranch, extraComments: List<Instruction>) {
        if (!writer.endsWith("else if (")) begin().append("if (")
        language.appendExpr(instr.expr.expr)
        writer.append(") {")
        for (i in extraComments.indices) {
            val comment = extraComments[i] as Comment
            writer.append(if (i == 0) " // " else ", ").append(comment.text)
        }
        writer.append('\n')
        depth++
        writeInstructions(instr.ifTrue)
        depth--
        val ifFalse = instr.ifFalse
        if (ifFalse.isNotEmpty()) {
            val i = nextInstr(ifFalse, -1)
            val ni = nextInstr(ifFalse, i)
            val instrI = ifFalse.getOrNull(i)
            if (ni == -1 && instrI is ExprIfBranch) {
                begin().append("} else if (")
                // continue if-else-cascade
                writeIfBranch(instrI, ifFalse.subList(0, i))
                // append any additional comments
                writeInstructions(ifFalse.subList(i + 1, ifFalse.size))
            } else {
                begin().append("} else {\n")
                depth++
                writeInstructions(ifFalse)
                depth--
                begin().append("}\n")
            }
        } else begin().append("}\n")
    }
}
