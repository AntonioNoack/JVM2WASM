package wasm2cpp

import gIndex
import hIndex
import hierarchy.HierarchyIndex.getAlias
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFail
import me.anno.utils.assertions.assertTrue
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
        private var debugInstructions = false
    }

    var depth = 0
    lateinit var function: FunctionImpl
    var isStatic = false
    var className: String = ""

    fun write(function: FunctionImpl, className: String, isStatic: Boolean) {

        this.function = function
        this.isStatic = isStatic
        this.className = className

        begin()
        language.writeFunctionStart(function, this)
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
        // writer.append("/* ").append(instr.javaClass).append(" */ ")
        when (instr) {
            is CppLoadInstr -> language.writeLoadInstr(instr, this)
            is CppStoreInstr -> language.writeStoreInstr(instr, this)
            is NullDeclaration -> writeNullDeclaration(instr)
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
                begin()
                language.writeReturnStruct(results.map { it.expr })
                writer.end()
            }
        }
    }

    private fun writeUnreachable() {
        begin().append("unreachable(\"")
            .append(function.funcName).append("\")").end()
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
                writer.append(instr.resultName).append(" = ")
            }
        }
    }

    private fun writeCalled(self: StackElement?, isSpecial: Boolean, sig: MethodSig) {
        var appendSuper = false
        if (isSpecial && !isStatic) {
            // todo is this good enough???
            val expr = self!!.expr
            if (expr is VariableExpr &&
                expr.name == function.params[0].name &&
                sig.className == hIndex.superClass[className]
            ) appendSuper = true
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
        writer.end()
    }

    private fun writeUnresolvedCallAssignment(instr: UnresolvedCallAssignment) {
        writeCallAssignmentBegin(instr)
        writeCalled(instr.self, instr.isSpecial, instr.sig)
        writer.append('.')
        language.appendExpr(CallExpr(instr.funcName, instr.params.map { it.expr }, instr.resultType ?: "?"))
        writer.end()
    }

    private fun writeExprCall(instr: ExprCall) {
        begin()
        language.appendExpr(CallExpr(instr.funcName, instr.params.map { it.expr }, "?"))
        writer.end()
    }

    private fun writeUnresolvedExprCall(instr: UnresolvedExprCall) {
        begin()
        writeCalled(instr.self, instr.isSpecial, instr.sig)
        writer.append('.')
        language.appendExpr(CallExpr(instr.funcName, instr.params.map { it.expr }, "?"))
        writer.end()
    }

    private fun writeLoopInstr(instr: LoopInstr) {
        begin()
        if (instr.label.isNotEmpty()) writer.append(instr.label).append(": ")
        writer.append("while (true) {\n")
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
