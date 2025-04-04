package wasm2cpp

import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFail
import optimizer.InstructionProcessor
import utils.StringBuilder2
import wasm.instr.*
import wasm.instr.Instructions.Unreachable
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
    }

    private var depth = 1
    lateinit var function: FunctionImpl

    private val usageCounts = HashMap<String, Int>()
    private val assignCounts = HashMap<String, Int>()
    private val declarationBlocker = HashSet<String>()

    private fun incAssign(name: String) {
        val delta = if (name in declarationBlocker) 10 else 1
        assignCounts[name] = (assignCounts[name] ?: 0) + delta
        declarationBlocker.add(name)
    }

    private fun incUsage(name: String) {
        usageCounts[name] = (usageCounts[name] ?: 0) + 1
        declarationBlocker.add(name)
    }

    private fun incUsages(names: List<String>) {
        for (i in names.indices) {
            incUsage(names[i])
        }
    }

    private fun incUsages(expr: StackElement) {
        incUsages(expr.names)
    }

    private fun getAssignCount(name: String): Int {
        return assignCounts[name] ?: 0
    }

    private fun getUsageCount(name: String): Int {
        return usageCounts[name] ?: 0
    }

    private val usageCounter = InstructionProcessor { instr ->
        when (instr) {
            is Assignment -> {
                incUsages(instr.newValue)
                incAssign(instr.name)
            }
            is Declaration -> {
                incUsages(instr.initialValue)
                incAssign(instr.name)
            }
            is CppLoadInstr -> incUsages(instr.addrExpr)
            is CppStoreInstr -> {
                incUsages(instr.addrExpr)
                incUsages(instr.valueExpr)
            }
            is ExprReturn -> {
                for (expr in instr.results) incUsages(expr)
            }
            is ExprCall -> {
                for (expr in instr.params) incUsages(expr)
                if (instr.resultName != null) incAssign(instr.resultName)
            }
            is FunctionTypeDefinition -> {
                incUsages(instr.indexExpr)
                incAssign(instr.instanceName)
            }
            is ExprIfBranch -> incUsages(instr.expr)
            BreakInstr, is GotoInstr, Unreachable, is NullDeclaration, is Comment, is LoopInstr -> {
                // nothing to do
            }
            else -> throw NotImplementedError("Unknown instruction $instr")
        }
    }

    // todo if a variable is written only once (except null-declaration),
    //  and only read afterward
    //  declare it in-place instead of previously, if possible

    fun write(function: FunctionImpl) {

        this.function = function
        assignCounts.clear()
        usageCounts.clear()
        declarationBlocker.clear()
        usageCounter.process(function)
        depth = 1

        defineFunctionHead(function, true)
        writer.append(" {\n")

        if (function.funcName.startsWith("static_")) {
            begin().append("static bool wasCalled = false").end()
            begin().append(
                if (function.results.isEmpty()) "if(wasCalled) return"
                else "if(wasCalled) return 0"
            ).end()
            begin().append("wasCalled = true").end()
        }

        if (false) {
            begin().append("// usages: ").append(usageCounts).append('\n')
            begin().append("// assigns: ").append(assignCounts).append('\n')
        }

        joinRedundantNullDeclarationsWithAssignments(function.body)
        writeInstructions(function.body, true)
        writer.append("}\n")
    }

    private val nullDeclNameToIndex = HashMap<String, Int>()
    private fun joinRedundantNullDeclarationsWithAssignments(body: ArrayList<Instruction>) {
        var i1 = 0
        val nameToIndex = nullDeclNameToIndex
        nameToIndex.clear()
        while (i1 < body.size) {
            val instr = body[i1]
            if (instr is NullDeclaration) {
                nameToIndex[instr.name] = i1
                i1++
            } else break
        }
        if (i1 == 0) return
        // replace all null-declarations with declarations with actual values
        var i2 = i1
        while (i2 < body.size) {
            val instr = body[i2]
            if (instr is Assignment) {
                val index = nameToIndex[instr.name]!!
                body[index] = Declaration(instr.type, instr.name, instr.newValue)
                i2++
            } else break
        }
        // then remove all the assignments, that are no longer needed
        body.subList(i1, i2).clear()
    }

    private fun begin(): StringBuilder2 {
        for (i in 0 until depth) writer.append("  ")
        return writer
    }

    private fun StringBuilder2.end() {
        append(";\n")
    }

    private fun writeInstructions(instructions: List<Instruction>, isLastInstr: Boolean) {
        if (instructions.isEmpty()) return
        var i = 0
        while (i >= 0) {
            val ni = nextInstr(instructions, i)
            val pos0 = writer.size
            val skipped = writeInstruction(
                instructions[i],
                instructions.getOrNull(ni),
                isLastInstr && i == instructions.lastIndex
            )
            if (skipped) {
                // restore comments in-between, so file length stays comparable
                val pos1 = writer.size
                for (j in i + 1 until ni) {
                    writeInstruction(instructions[j], null, false)
                }
                // these should be inserted before, not after
                swap(pos0, pos1)
            }
            i = if (skipped) nextInstr(instructions, ni) else ni
        }
    }

    private fun swap(pos0: Int, pos1: Int) {
        val writer = writer
        writer.append(writer, pos0, pos1)
        writer.remove(pos0, pos1)
    }

    private fun canInlineDeclaration(name: String): Boolean {
        return getAssignCount(name) == 1 // todo also verify that all usages are after/below the assignment
    }

    private fun isUnused(name: String): Boolean {
        return name !in usageCounts
    }

    private fun writeInstruction(instr: Instruction, nextInstr: Instruction?, isLastInstr: Boolean): Boolean {
        if (false) {
            begin().append("/* ").append(instr.javaClass.simpleName)
            if (instr is Declaration) writer.append(", ").append(instr.initialValue.names)
            writer.append(" */\n")
        }
        when (instr) {
            is CppLoadInstr -> {
                begin().append(instr.type).append(' ').append(instr.newName).append(" = ")
                    .append("((").append(instr.memoryType).append("*) ((uint8_t*) memory + (u32)")
                    .appendExpr(instr.addrExpr).append("))[0]").end()
            }
            is CppStoreInstr -> {
                begin().append("((").append(instr.memoryType).append("*) ((uint8_t*) memory + (u32)")
                    .appendExpr(instr.addrExpr).append("))[0] = ")
                if (instr.type != instr.memoryType) {
                    writer.append('(').append(instr.memoryType).append(") ")
                }
                writer.append(instr.valueExpr.expr).end()
            }
            is NullDeclaration -> {
                begin()
                if (!canInlineDeclaration(instr.name)) {
                    if (isUnused(instr.name)) writer.append("// unused: ")
                } else {
                    if (isUnused(instr.name)) {
                        writer.append("// unused, inlined: ")
                    } else {
                        writer.append("// inlined: ")
                    }
                }
                writer.append(instr.type).append(' ').append(instr.name)
                    .append(" = 0").end()
            }
            is Declaration -> {
                begin()
                val unused = isUnused(instr.name)
                if (unused) writer.append("// unused: ")
                val inlineReturn = !unused &&
                        nextInstr is ExprReturn && nextInstr.results.size == 1 &&
                        nextInstr.results[0].expr == instr.name
                if (inlineReturn) {
                    writer.append("return ")
                } else {
                    writer.append(instr.type).append(' ').append(instr.name).append(" = ")
                }
                writer.append(instr.initialValue.expr).end()
                return inlineReturn
            }
            is Assignment -> {
                begin()
                if (isUnused(instr.name)) writer.append("// unused: ")
                if (canInlineDeclaration(instr.name)) {
                    writer.append(instr.type).append(' ')
                }
                writer.append(instr.name)
                    .append(" = ").append(instr.newValue.expr).end()
            }
            is ExprReturn -> {
                val results = instr.results
                assertEquals(function.results.size, results.size)
                when (results.size) {
                    0 -> {
                        if (!isLastInstr) begin().append("return").end()
                        // else skipped return
                    }
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
            Unreachable -> {
                begin().append("unreachable(\"")
                    .append(function.funcName).append("\")").end()
            }
            is ExprIfBranch -> writeIfBranch(instr, emptyList(), isLastInstr)
            is ExprCall -> {

                val returnsImmediately =
                    if (nextInstr is ExprReturn) {
                        nextInstr.results.size == 1 && nextInstr.results[0].expr == instr.resultName
                    } else false

                val assignsImmediately =
                    instr.resultTypes.size == 1 && usageCounts[instr.resultName] == 1 &&
                            when (nextInstr) {
                                is Assignment -> nextInstr.newValue.expr == instr.resultName
                                is Declaration -> nextInstr.initialValue.expr == instr.resultName
                                else -> false
                            }

                begin()

                if (returnsImmediately) {
                    writer.append("return ")
                } else if (instr.resultName != null && getUsageCount(instr.resultName) > 0) {
                    if (assignsImmediately) {
                        val variableName = when (nextInstr) {
                            is Assignment -> nextInstr.name
                            is Declaration -> nextInstr.name
                            else -> throw NotImplementedError()
                        }
                        val unused = isUnused(variableName)
                        if (unused) writer.append("/* unused: ")
                        if (nextInstr is Declaration || canInlineDeclaration(variableName)) {
                            writer.append(instr.resultTypes[0]).append(' ')
                        }
                        writer.append(variableName).append(" = ")
                        if (unused) writer.append(" */")
                    } else {
                        for (r in instr.resultTypes) { // combine result types into struct name
                            writer.append(r)
                        }
                        writer.append(' ').append(instr.resultName).append(" = ")
                    }
                }

                writer.append(instr.funcName).append('(')
                for (param in instr.params) {
                    if (!writer.endsWith("(")) writer.append(", ")
                    writer.append(param.expr)
                }
                writer.append(")").end()

                return returnsImmediately || assignsImmediately
            }
            is FunctionTypeDefinition -> {
                val type = instr.funcType
                val tmpType = instr.typeName
                val tmpVar = instr.instanceName
                // using CalculateFunc = int32_t(*)(int32_t, int32_t, float);
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
                // CalculateFunc calculateFunc = reinterpret_cast<CalculateFunc>(funcPtr);
                begin().append(tmpType).append(' ').append(tmpVar).append(" = reinterpret_cast<")
                    .append(tmpType).append(">(indirect[").append(instr.indexExpr.expr).append("])").end()
            }
            is BlockInstr -> {
                // write body
                writeInstructions(instr.body, isLastInstr)
                // write label at end as jump target
                begin().append(instr.label).append(":\n")
            }
            is LoopInstr -> {
                begin().append(instr.label).append(": while (true) {\n")
                depth++
                writeInstructions(instr.body, false)
                depth--
                begin().append("}\n")
            }
            is GotoInstr -> begin().append("goto ").append(instr.label).end()
            is BreakInstr -> begin().append("break").end()
            is SwitchCase -> writeSwitchCase(instr, isLastInstr)
            is Comment -> begin().append("// ").append(instr.name).append('\n')
            else -> assertFail("Unknown instruction type ${instr.javaClass}")
        }
        return false
    }

    private fun writeIfBranch(instr: ExprIfBranch, extraComments: List<Instruction>, isLastInstr: Boolean) {
        if (!writer.endsWith("else if(")) begin().append("if (")
        writer.append(instr.expr.expr).append(") {")
        for (i in extraComments.indices) {
            val comment = extraComments[i] as Comment
            writer.append(if (i == 0) " // " else ", ").append(comment.name)
        }
        writer.append('\n')
        depth++
        writeInstructions(instr.ifTrue, isLastInstr)
        depth--
        val ifFalse = instr.ifFalse
        if (ifFalse.isNotEmpty()) {
            val i = nextInstr(ifFalse, -1)
            val ni = nextInstr(ifFalse, i)
            val instrI = ifFalse.getOrNull(i)
            if (ni == -1 && instrI is ExprIfBranch) {
                begin().append("} else if(")
                // continue if-else-cascade
                writeIfBranch(instrI, ifFalse.subList(0, i), isLastInstr)
                // append any additional comments
                writeInstructions(ifFalse.subList(i + 1, ifFalse.size), false)
            } else {
                begin().append("} else {\n")
                depth++
                writeInstructions(ifFalse, isLastInstr)
                depth--
                begin().append("}\n")
            }
        } else begin().append("}\n")
    }

    private fun writeSwitchCase(switchCase: SwitchCase, isLastInstr: Boolean) {
        val cases = switchCase.cases
        for (j in cases.indices) {
            begin().append("case").append(j).append(": {\n")
            depth++
            writeInstructions(cases[j], isLastInstr)
            depth--
            begin().append("}\n")
        }
    }
}
