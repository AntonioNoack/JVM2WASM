package wasm2cpp

import me.anno.utils.assertions.assertFail
import optimizer.InstructionProcessor
import translator.JavaTypes.convertTypeToWASM
import wasm.instr.*
import wasm.instr.Instructions.Unreachable
import wasm.parser.FunctionImpl
import wasm.parser.GlobalVariable
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

class DeclarativeOptimizer(val globals: Map<String, GlobalVariable>) {

    companion object {
        private var debugInstructions = false
    }

    private var depth = 1
    lateinit var function: FunctionImpl

    private val usageCounts = HashMap<String, Int>()
    private val assignCounts = HashMap<String, Int>()
    private val declarationBlocker = HashSet<String>()

    val writer = ArrayList<Instruction>()

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
            BreakThisLoopInstr, is GotoInstr, Unreachable, is NullDeclaration, is Comment, is LoopInstr -> {
                // nothing to do
            }
            else -> throw NotImplementedError("Unknown instruction $instr")
        }
    }

    fun clear() {
        writer.clear()
        assignCounts.clear()
        usageCounts.clear()
        declarationBlocker.clear()
        depth = 1
    }

    fun write(function: FunctionImpl): ArrayList<Instruction> {

        clear()
        this.function = function
        usageCounter.process(function)

        if (debugInstructions) writeDebugUsages()

        joinRedundantNullDeclarationsWithAssignments(function.body)
        writeInstructions(function.body, true)

        removeRedundantAssignments(writer)

        return ArrayList(writer)
    }

    private fun removeRedundantAssignments(instructions: ArrayList<Instruction>) {
        val previousAssignments = HashMap<String, Int>()
        var i = 0
        while (i < instructions.size) {
            fun removeNames(expr: StackElement) {
                for (name in expr.names) previousAssignments.remove(name)
            }
            when (val instr = instructions[i]) {
                is Assignment -> {
                    removeNames(instr.newValue)
                    val previous = previousAssignments[instr.name]
                    if (previous != null && previous < i) {
                        when (instructions[previous]) {
                            is NullDeclaration, is Declaration -> {
                                instructions[i] = Declaration(instr.type, instr.name, instr.newValue)
                                instructions[previous] = NopInstr
                            }
                            is Assignment -> {
                                instructions[previous] = NopInstr
                            }
                            else -> throw NotImplementedError()
                        }
                    }
                    previousAssignments[instr.name] = i
                }
                is Declaration -> {
                    removeNames(instr.initialValue)
                    previousAssignments[instr.name] = i
                }
                is NullDeclaration -> {
                    previousAssignments[instr.name] = i
                }
                is ExprIfBranch -> {
                    removeRedundantAssignments(instr.ifTrue)
                    removeRedundantAssignments(instr.ifFalse)
                    previousAssignments.clear()
                }
                is LoopInstr -> {
                    removeRedundantAssignments(instr.body)
                    previousAssignments.clear()
                }
                is IfBranch, is Jumping, is CppStoreInstr,
                is ExprCall, is GotoInstr, is ExprReturn, Unreachable -> {
                    previousAssignments.clear()
                }
                BreakThisLoopInstr, is CppLoadInstr, is FunctionTypeDefinition, is Comment -> {}
                else -> throw NotImplementedError("${instr.javaClass} not yet implemented")
            }
            i++
        }

        instructions.removeIf { it == NopInstr }
    }

    private fun writeDebugUsages() {
        writer.add(Comment("usages: $usageCounts"))
        writer.add(Comment("assigns: $assignCounts"))
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

    private fun writeInstructions(instructions: List<Instruction>, canBeLastInstr: Boolean) {
        if (instructions.isEmpty()) return
        var i = 0
        while (i >= 0) {
            val ni = nextInstr(instructions, i)
            val pos0 = writer.size
            val skipped = writeInstruction(
                instructions[i], instructions.getOrNull(ni),
                isLastInstr = canBeLastInstr && i == instructions.lastIndex
            )
            if (skipped) {
                // restore comments in-between, so file length stays comparable
                val pos1 = writer.size
                for (j in i + 1 until ni) {
                    writeInstruction(instructions[j] as Comment, null, false)
                }
                // these should be inserted before, not after
                swap(pos0, pos1)
            }
            i = if (skipped) nextInstr(instructions, ni) else ni
        }
    }

    private fun swap(pos0: Int, pos1: Int) {
        val writer = writer
        writer.addAll(writer.subList(pos0, pos1))
        writer.subList(pos0, pos1).clear()
    }

    private fun canInlineDeclaration(name: String): Boolean {
        // to do also verify that all usages are after/below the assignment
        //  -> was done implicitly...
        return getAssignCount(name) == 1
    }

    private fun isUnused(name: String): Boolean {
        return name !in usageCounts
    }

    private fun writeInstruction(instr: Instruction, nextInstr: Instruction?, isLastInstr: Boolean): Boolean {
        if (debugInstructions) writeDebugInfo(instr)
        when (instr) {
            is Comment,
            Unreachable,
            is FunctionTypeDefinition,
            is GotoInstr, BreakThisLoopInstr,
            is CppLoadInstr,
            is CppStoreInstr -> writer.add(instr)
            is ExprReturn -> writeExprReturn(instr, isLastInstr)
            is NullDeclaration -> writeNullDeclaration(instr)
            is Declaration -> return writeDeclaration(instr, nextInstr)
            is Assignment -> writeAssignment(instr)
            is ExprIfBranch -> writeIfBranch(instr, isLastInstr)
            is ExprCall -> return writeExprCall(instr, nextInstr)
            is LoopInstr -> writeLoopInstr(instr)
            else -> assertFail("Unknown instruction type ${instr.javaClass}")
        }
        return false
    }

    private fun writeDebugInfo(instr: Instruction) {
        val text = if (instr is Declaration) {
            "${instr.javaClass.simpleName}, ${instr.initialValue.names}"
        } else instr.javaClass.simpleName
        writer.add(Comment(text))
    }

    private fun writeExprReturn(instr: ExprReturn, isLastInstr: Boolean) {
        if (instr.results.isNotEmpty() || !isLastInstr) writer.add(instr)
    }

    private fun writeNullDeclaration(instr: NullDeclaration) {
        val unused = isUnused(instr.name)
        val canBeInlined = canInlineDeclaration(instr.name)
        val isComment = unused || canBeInlined
        if (!debugInstructions && isComment) return

        val prefix = if (!canBeInlined) {
            "// unused: "
        } else {
            if (unused) {
                "// unused, inlined: "
            } else {
                "// inlined: "
            }
        }

        if (isComment) {
            writer.add(Comment("$prefix${instr.jvmType} ${instr.name} = 0"))
        } else {
            val zero = StackElement(instr.jvmType, "0", emptyList(), false)
            writer.add(Declaration(instr.jvmType, instr.name, zero))
        }
    }

    private fun writeDeclaration(instr: Declaration, nextInstr: Instruction?): Boolean {
        val unused = isUnused(instr.name)
        if (unused && !debugInstructions) return false
        val inlineReturn = !unused &&
                nextInstr is ExprReturn && nextInstr.results.size == 1 &&
                nextInstr.results[0].expr == instr.name
        val newInstr = if (unused) {
            Comment("unused: ${instr.type} ${instr.name} = ${instr.initialValue.expr}")
        } else if (inlineReturn) {
            ExprReturn(listOf(instr.initialValue))
        } else instr
        writer.add(newInstr)
        return inlineReturn
    }

    private fun writeAssignment(instr: Assignment) {
        val unused = isUnused(instr.name)
        if (unused && !debugInstructions) return
        val inline = canInlineDeclaration(instr.name)
        val newInstr = if (unused) {
            val text = if (inline) {
                "unused: ${instr.type} ${instr.name} = ${instr.newValue.expr}"
            } else {
                "unused: ${instr.name} = ${instr.newValue.expr}"
            }
            Comment(text)
        } else if (inline) {
            Declaration(instr.type, instr.name, instr.newValue)
        } else instr
        writer.add(newInstr)
    }

    private fun writeExprCall(instr: ExprCall, nextInstr: Instruction?): Boolean {
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

        var resultType: String? = null
        var resultName: String? = null

        if (returnsImmediately) {
            resultType = ExprCall.RETURN_TYPE
        } else if (instr.resultName != null && getUsageCount(instr.resultName) > 0) {
            if (assignsImmediately) {
                val variableName = when (nextInstr) {
                    is Assignment -> nextInstr.name
                    is Declaration -> nextInstr.name
                    else -> throw NotImplementedError()
                }
                val unused = isUnused(variableName)
                val declaration = nextInstr is Declaration || canInlineDeclaration(variableName)
                if (unused) {
                    val text = if (declaration) {
                        nextInstr as Declaration
                        "unused: ${nextInstr.type} $variableName ="
                    } else {
                        "unused: $variableName ="
                    }
                    writer.add(Comment(text))
                } else {
                    resultName = variableName
                    if (declaration) {
                        resultType = convertTypeToWASM(instr.resultTypes[0]).wasmName
                    }
                }
            } else {
                resultName = instr.resultName
                resultType = instr.resultTypes.joinToString("") {
                    // combine result types into struct name
                    convertTypeToWASM(it).wasmName
                }
            }
        }

        writer.add(ExprCall(instr.funcName, instr.params, instr.resultTypes, resultName, resultType))
        return returnsImmediately || assignsImmediately
    }

    private fun writeLoopInstr(instr: LoopInstr) {
        val i0 = writer.size
        writeInstructions(instr.body, false)
        writer.add(instr.withBody(getSubList(i0)))
    }

    private fun getSubList(i0: Int): ArrayList<Instruction> {
        val tmpList = writer.subList(i0, writer.size)
        val result = ArrayList(tmpList)
        tmpList.clear()
        return result
    }

    private fun writeIfBranch(instr: ExprIfBranch, isLastInstr: Boolean) {
        val i0 = writer.size
        writeInstructions(instr.ifTrue, isLastInstr)
        val ifTrue = getSubList(i0)
        val i1 = writer.size
        writeInstructions(instr.ifFalse, isLastInstr)
        val ifFalse = getSubList(i1)
        writer.add(ExprIfBranch(instr.expr, ifTrue, ifFalse))
    }
}
