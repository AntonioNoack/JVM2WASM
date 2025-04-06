package wasm2cpp

import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFail
import me.anno.utils.assertions.assertTrue
import utils.StringBuilder2
import wasm.instr.Comment
import wasm.instr.Instruction
import wasm.instr.Instructions.Unreachable
import wasm.instr.LoopInstr
import wasm.parser.FunctionImpl
import wasm.parser.GlobalVariable
import wasm2cpp.StackToDeclarative.Companion.appendExpr
import wasm2cpp.StackToDeclarative.Companion.nextInstr
import wasm2cpp.instr.*

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

class FunctionWriter(val globals: Map<String, GlobalVariable>) {

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

    private var depth = 1
    lateinit var function: FunctionImpl

    fun write(function: FunctionImpl) {

        this.function = function
        depth = 1

        defineFunctionHead(function, true)
        writer.append(" {\n")

        if (function.funcName.startsWith("static_")) {
            writeStaticInitCheck()
        }

        writeInstructions(function.body)
        writer.append("}\n")
    }

    private fun writeStaticInitCheck() {
        begin().append("static bool wasCalled = false").end()
        begin().append(
            if (function.results.isEmpty()) "if(wasCalled) return"
            else "if(wasCalled) return 0"
        ).end()
        begin().append("wasCalled = true").end()
    }

    private fun begin(): StringBuilder2 {
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
            is ExprReturn -> writeExprReturn(instr)
            Unreachable -> writeUnreachable()
            is ExprIfBranch -> writeIfBranch(instr, emptyList())
            is ExprCall -> writeExprCall(instr)
            is FunctionTypeDefinition -> writeFunctionTypeDefinition(instr)
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
        begin().append("goto ").append(instr.label).end()
    }

    private fun writeBreakThisLoop() {
        begin().append("break").end()
    }

    private fun writeDebugInfo(instr: Instruction) {
        begin().append("/* ").append(instr.javaClass.simpleName)
        if (instr is Declaration) writer.append(", ").append(instr.initialValue.names)
        writer.append(" */\n")
    }

    private fun writeLoadInstr(instr: CppLoadInstr) {
        begin().append(instr.type).append(' ').append(instr.newName).append(" = ")
            .append("((").append(instr.memoryType).append("*) ((uint8_t*) memory + (u32)")
            .appendExpr(instr.addrExpr).append("))[0]").end()
    }

    private fun writeStoreInstr(instr: CppStoreInstr) {
        begin().append("((").append(instr.memoryType).append("*) ((uint8_t*) memory + (u32)")
            .appendExpr(instr.addrExpr).append("))[0] = ")
        if (instr.type != instr.memoryType) {
            writer.append('(').append(instr.memoryType).append(") ")
        }
        writer.append(instr.valueExpr.expr).end()
    }

    private fun writeNullDeclaration(instr: NullDeclaration) {
        begin().append(instr.jvmType).append(' ').append(instr.name)
            .append(" = 0").end()
    }

    private fun writeDeclaration(instr: Declaration) {
        begin()
            .append(instr.type).append(' ').append(instr.name).append(" = ")
            .append(instr.initialValue.expr).end()
    }

    private fun writeAssignment(instr: Assignment) {
        begin().append(instr.name).append(" = ").append(instr.newValue.expr).end()
    }

    private fun writeExprReturn(instr: ExprReturn) {
        val results = instr.results
        assertEquals(function.results.size, results.size)
        when (results.size) {
            0 -> begin().append("return").end()
            1 -> begin().append("return ").append(results.first().expr).end()
            else -> {
                begin().append("return { ")
                for (ri in results.indices) {
                    if (ri > 0) writer.append(", ")
                    writer.append(results[ri].expr)
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
                writer.append(instr.resultType).append(' ')
            }
            writer.append(instr.resultName).append(" = ")
        }

        writer.append(instr.funcName).append('(')
        for (param in instr.params) {
            if (!writer.endsWith("(")) writer.append(", ")
            writer.append(param.expr)
        }
        writer.append(")").end()
    }

    private fun writeFunctionTypeDefinition(instr: FunctionTypeDefinition) {
        val type = instr.funcType
        val tmpType = instr.typeName
        val tmpVar = instr.instanceName
        // using CalculateFunc = int32_t(*)(int32_t, int32_t, float);
        // CalculateFunc calculateFunc = reinterpret_cast<CalculateFunc>(funcPtr);
        begin().append("using ").append(tmpType).append(" = ")
        if (type.results.isEmpty()) {
            writer.append("void")
        } else {
            for (ri in type.results.indices) {
                writer.append(type.results[ri])
            }
        }
        writer.append("(*)(")
        for (pi in type.params.indices) {
            if (pi > 0) writer.append(", ")
            writer.append(type.params[pi])
        }
        writer.append(")").end()
        begin().append(tmpType).append(' ').append(tmpVar).append(" = reinterpret_cast<")
            .append(tmpType).append(">(indirect[").append(instr.indexExpr.expr).append("])").end()
    }

    private fun writeLoopInstr(instr: LoopInstr) {
        begin().append(instr.label).append(": while (true) {\n")
        depth++
        writeInstructions(instr.body)
        depth--
        begin().append("}\n")
    }

    private fun writeIfBranch(instr: ExprIfBranch, extraComments: List<Instruction>) {
        if (!writer.endsWith("else if(")) begin().append("if (")
        writer.append(instr.expr.expr).append(") {")
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
                begin().append("} else if(")
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
