package wasm2cpp

import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFail
import me.anno.utils.assertions.assertTrue
import utils.INSTANCE_INIT
import utils.MethodSig
import utils.StringBuilder2
import wasm.instr.Comment
import wasm.instr.Instruction
import wasm.instr.Instructions.Unreachable
import wasm.instr.LoopInstr
import wasm.parser.FunctionImpl
import wasm.parser.GlobalVariable
import wasm2cpp.StackToDeclarative.Companion.nextInstr
import wasm2cpp.expr.CallExpr
import wasm2cpp.expr.VariableExpr
import wasm2cpp.instr.*
import wasm2cpp.language.HighLevelJavaScript
import wasm2cpp.language.TargetLanguage
import wasm2js.minifyJavaScript

class FunctionWriter(val globals: Map<String, GlobalVariable>, val language: TargetLanguage) {

    companion object {
        private var debugInstructions = false
    }

    var depth = 0
    lateinit var function: FunctionImpl
    var isStatic = false
    var className: String = ""
    var originalName: String = ""

    fun write(function: FunctionImpl, className: String, originalName: String, isStatic: Boolean) {

        this.function = function
        this.isStatic = isStatic
        this.className = className
        this.originalName = originalName

        begin()
        language.writeFunctionStart(function, this)
        depth++

        if (originalName.startsWith("static_")) {
            language.writeStaticInitCheck(this)
        }

        val body = function.body
        var i = 0
        while (i < body.size && body[i] is Declaration) i++
        @Suppress("UNCHECKED_CAST")
        if (i > 0) language.writeDeclarations(this, body.subList(0, i) as List<Declaration>)
        writeInstructions(body, i, body.size)

        depth--
        begin().append("}")
        language.ln()
    }

    fun begin(): StringBuilder2 {
        if (!(language is HighLevelJavaScript && minifyJavaScript)) {
            for (i in 0 until depth) writer.append("  ")
        }
        return writer
    }

    private fun writeInstructions(instructions: List<Instruction>) {
        writeInstructions(instructions, 0, instructions.size)
    }

    private fun writeInstructions(instructions: List<Instruction>, i0: Int, i1: Int) {
        for (i in i0 until i1) {
            writeInstruction(instructions[i])
        }
    }

    private fun writeInstruction(instr: Instruction) {
        if (debugInstructions) writeDebugInfo(instr)
        // writer.append("/* ").append(instr.javaClass).append(" */ ")
        when (instr) {
            is CppLoadInstr -> language.writeLoadInstr(instr, this)
            is CppStoreInstr -> language.writeStoreInstr(instr, this)
            is Declaration -> writeDeclaration(instr)
            is Assignment -> writeAssignment(instr)
            is FieldAssignment -> writeFieldAssignment(instr)
            is ExprReturn -> writeExprReturn(instr)
            Unreachable -> writeUnreachable()
            is ExprIfBranch -> writeIfBranch(instr, emptyList())
            is UnresolvedCallAssignment -> writeUnresolvedCallAssignment(instr)
            is CallAssignment -> writeCallAssignment(instr)
            is UnresolvedExprCall -> writeUnresolvedExprCall(instr)
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
        if (language is HighLevelJavaScript && minifyJavaScript) return
        assertTrue('\n' !in instr.text)
        begin().append("// ").append(instr.text).append('\n')
    }

    private fun writeGoto(instr: GotoInstr) {
        begin()
        language.writeGoto(instr)
        language.end()
    }

    private fun writeBreakThisLoop() {
        begin().append("break")
        language.end()
    }

    private fun writeDebugInfo(instr: Instruction) {
        begin().append("/* ").append(instr.javaClass.simpleName)
        if (instr is Declaration) writer.append(", ").append(instr.initialValue.dependencies)
        writer.append(" */")
        language.ln()
    }

    private fun writeDeclaration(instr: Declaration) {
        begin()
        language.beginDeclaration(instr.name, instr.jvmType)
        language.appendExpr(instr.initialValue.expr)
        language.end()
    }

    private fun writeAssignment(assignment: Assignment) {
        begin()
        language.beginAssignment(assignment.name)
        language.appendExpr(assignment.newValue.expr)
        language.end()
    }

    private fun writeFieldAssignment(assignment: FieldAssignment) {
        language.writeFieldAssignment(assignment, this)
    }

    private fun writeExprReturn(instr: ExprReturn) {
        val results = instr.results
        assertEquals(function.results.size, results.size)
        when (results.size) {
            0 -> begin().append("return")
            1 -> {
                begin().append("return ")
                language.appendExpr(results.first().expr)
            }
            else -> {
                begin()
                language.writeReturnStruct(results.map { it.expr })
            }
        }
        language.end()
    }

    private fun writeUnreachable() {
        begin()
        language.writeUnreachable(this)
    }

    private fun writeCallAssignmentBegin(instr: CallAssignment) {
        begin()

        /*writer.append("/* ").append(instr.resultName ?: "null")
            .append(", ").append(instr.resultType ?: "null").append(" */ ")*/

        if (instr.isReturn) {
            writer.append("return ")
        } else if (instr.resultName != null) {
            if (instr.resultType != null) {
                language.beginDeclaration(instr.resultName, instr.resultType)
            } else {
                language.beginAssignment(instr.resultName)
            }
        }
    }

    private fun writeCalled(self: StackElement?, isSpecial: Boolean, sig: MethodSig) {
        var appendSuper = false
        if (isSpecial && !isStatic) {
            // todo is this good enough???
            val expr = self!!.expr
            if (expr is VariableExpr &&
                (expr.name == function.params[0].name || sig.name != INSTANCE_INIT) &&
                (sig.className != className)
            ) {
                appendSuper = true
            }
        }
        if (appendSuper) {
            writer.append("super")
        } else if (self != null) {
            language.appendExpr(self.expr)
        } else {
            language.writeStaticInstance(sig.className)
        }
    }

    private fun writeCallAssignment(instr: CallAssignment) {
        writeCallAssignmentBegin(instr)
        language.appendExpr(CallExpr(instr.funcName, instr.params.map { it.expr }, instr.resultType ?: "?"))
        language.end()
    }

    private fun writeUnresolvedCallAssignment(instr: UnresolvedCallAssignment) {
        writeCallAssignmentBegin(instr)
        writeCalled(instr.self, instr.isSpecial, instr.sig)
        writer.append('.')
        language.appendExpr(CallExpr(instr.funcName, instr.params.map { it.expr }, instr.resultType ?: "?"))
        language.end()
    }

    private fun writeExprCall(instr: ExprCall) {
        begin()
        language.appendExpr(CallExpr(instr.funcName, instr.params.map { it.expr }, "?"))
        language.end()
    }

    private fun writeUnresolvedExprCall(instr: UnresolvedExprCall) {
        begin()
        writeCalled(instr.self, instr.isSpecial, instr.sig)
        writer.append('.')
        language.appendExpr(CallExpr(instr.funcName, instr.params.map { it.expr }, "?"))
        language.end()
    }

    private fun writeLoopInstr(instr: LoopInstr) {
        val compact = language is HighLevelJavaScript && minifyJavaScript
        begin()
        if (instr.label.isNotEmpty()) {
            language.appendName(instr.label)
            writer.append(if (compact) ":" else ": ")
        }
        writer.append(if (compact) "for(;;){" else "while (true) {")
        language.ln()
        depth++
        writeInstructions(instr.body)
        depth--
        begin().append("}")
        language.ln()
    }

    private fun writeIfBranch(instr: ExprIfBranch, extraComments: List<Instruction>) {
        val compact = language is HighLevelJavaScript && minifyJavaScript
        if (!writer.endsWith(if (compact) "else if(" else "else if (")) {
            begin().append(if (compact) "if(" else "if (")
        }
        language.appendExpr(instr.expr.expr)
        writer.append(if (compact) "){" else ") {")
        if (!(language is HighLevelJavaScript && minifyJavaScript)) {
            for (i in extraComments.indices) {
                val comment = extraComments[i] as Comment
                writer.append(if (i == 0) " // " else ", ").append(comment.text)
            }
            language.ln()
        }
        depth++
        writeInstructions(instr.ifTrue)
        depth--
        val ifFalse = instr.ifFalse
        if (ifFalse.isNotEmpty()) {
            val i = nextInstr(ifFalse, -1)
            val ni = nextInstr(ifFalse, i)
            val instrI = ifFalse.getOrNull(i)
            if (ni == -1 && instrI is ExprIfBranch) {
                begin().append(if (compact) "}else if(" else "} else if (")
                // continue if-else-cascade
                writeIfBranch(instrI, ifFalse.subList(0, i))
                // append any additional comments
                writeInstructions(ifFalse, i + 1, ifFalse.size)
            } else {
                begin().append(if (compact) "}else{" else "} else {")
                language.ln()
                depth++
                writeInstructions(ifFalse)
                depth--
                begin().append("}")
                language.ln()
            }
        } else {
            begin().append("}")
            language.ln()
        }
    }
}
