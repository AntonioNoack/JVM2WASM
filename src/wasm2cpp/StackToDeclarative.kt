package wasm2cpp

import canThrowError
import gIndex
import hIndex
import hierarchy.HierarchyIndex.getAlias
import highlevel.*
import me.anno.utils.Warning.unused
import me.anno.utils.assertions.*
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.structures.lists.Lists.pop
import me.anno.utils.types.Booleans.toInt
import org.apache.logging.log4j.LogManager
import translator.JavaTypes.convertTypeToWASM
import utils.*
import utils.WASMTypes.*
import wasm.instr.*
import wasm.instr.Instruction.Companion.emptyArrayList
import wasm.instr.Instructions.F32Load
import wasm.instr.Instructions.F32Store
import wasm.instr.Instructions.F64Load
import wasm.instr.Instructions.F64Store
import wasm.instr.Instructions.I32Load
import wasm.instr.Instructions.I32Load16S
import wasm.instr.Instructions.I32Load16U
import wasm.instr.Instructions.I32Load8S
import wasm.instr.Instructions.I32Load8U
import wasm.instr.Instructions.I32Store
import wasm.instr.Instructions.I32Store16
import wasm.instr.Instructions.I32Store8
import wasm.instr.Instructions.I64Load
import wasm.instr.Instructions.I64Store
import wasm.instr.Instructions.Return
import wasm.instr.Instructions.Unreachable
import wasm.parser.FunctionImpl
import wasm.parser.GlobalVariable
import wasm.parser.LocalVariable
import wasm2cpp.expr.*
import wasm2cpp.instr.*

/**
 * Converts stack-based style from WASM into the typical declarative style with inline expressions
 * */
class StackToDeclarative(
    val globals: Map<String, GlobalVariable>,
    private val functionsByName: Map<String, FunctionImpl>,
    private val pureFunctions: Set<String>,
    private val useHighLevelMemoryAccess: Boolean,
    private val useHighLevelMethodResolution: Boolean
) {

    companion object {

        private val LOGGER = LogManager.getLogger(StackToDeclarative::class)
        private val zeroForStaticInited = StackElement(ConstExpr(0, i32), emptyList(), true)

        /**
         * find the next instruction after i
         * */
        fun nextInstr(instructions: List<Instruction>, i: Int): Int {
            for (j in i + 1 until instructions.size) {
                val instr = instructions[j]
                if (instr !is Comment) return j
            }
            return -1
        }

        fun isNameOrNumber(expr: Expr): Boolean {
            return isNumber(expr) || expr is VariableExpr
        }

        fun isNumber(expr: Expr): Boolean {
            return expr is ConstExpr && expr.value is Number
        }

        fun canAppendWithoutBrackets(expr: Expr, operator: BinaryOperator, isLeft: Boolean): Boolean {
            return when (operator) {
                BinaryOperator.ADD -> {
                    expr is BinaryExpr && expr.instr is BinaryInstruction &&
                            when (expr.instr.operator) {
                                BinaryOperator.ADD, BinaryOperator.SUB,
                                BinaryOperator.MULTIPLY, BinaryOperator.DIVIDE, BinaryOperator.REMAINDER -> true
                                else -> false
                            }
                }
                BinaryOperator.SUB -> {
                    expr is BinaryExpr && expr.instr is BinaryInstruction &&
                            when (expr.instr.operator) {
                                BinaryOperator.MULTIPLY, BinaryOperator.DIVIDE, BinaryOperator.REMAINDER -> true
                                BinaryOperator.ADD -> isLeft
                                else -> false
                            }
                }
                BinaryOperator.MULTIPLY -> {
                    expr is BinaryExpr && expr.instr is BinaryInstruction &&
                            when (expr.instr.operator) {
                                BinaryOperator.MULTIPLY -> true
                                else -> false
                            }
                }
                // / -> no symbols are supported
                /* "^" -> {
                     val symbols = extractSymbolsFromExpression(expr)
                     return symbols.all { it == '^' }
                 }
                 "!" -> {
                     val symbols = extractSymbolsFromExpression(expr)
                     return symbols.all { it == '!' }
                 }
                 "<=", "<", ">", ">=", "!=", "==" -> {
                     val symbols = extractSymbolsFromExpression(expr)
                     return symbols.all { it in "+*-/" }
                 }*/
                /*"&" -> {
                    // todo this is somehow incorrect: we're getting a segfault, when using this
                    val symbols = extractSymbolsFromExpression(expr)
                    // theoretically, '|' is supported, but I want clear code
                    return symbols.all { it == '&' }
                }*/
                BinaryOperator.AND, BinaryOperator.XOR, BinaryOperator.OR -> {
                    expr is BinaryExpr && expr.instr is BinaryInstruction &&
                            expr.instr.operator == operator
                }
                else -> false
            }
        }
    }

    private var localsByName: Map<String, LocalVariable> = emptyMap()

    private val stack = ArrayList<StackElement>()
    private var genI = 0

    private val writer = ArrayList<ArrayList<Instruction>>()

    fun append(instruction: Instruction) {
        writer.last().add(instruction)
    }

    lateinit var function: FunctionImpl

    private fun init(function: FunctionImpl) {
        this.function = function
        localsByName = function.locals
            .associateBy { it.name }
        stack.clear()
        genI = 0
    }

    fun write(function: FunctionImpl): ArrayList<Instruction> {

        init(function)

        writer.add(ArrayList())

        for (local in function.locals) {
            if (local.name == "lbl") continue
            append(NullDeclaration(local.jvmType, local.name))
        }

        if (!writeInstructions(function.body)) {
            LOGGER.warn("Appended return to ${function.funcName}")
            writeInstruction(Return)
        }

        assertEquals(1, writer.size)
        return writer.removeLast()
    }

    private fun nextTemporaryVariable(): String = "tmp${genI++}"

    private fun popInReverse(funcName: String, types: List<String>): List<StackElement> {
        assertTrue(stack.size >= types.size) { "Expected $types for $funcName, got $stack" }
        val offset = stack.size - types.size
        val hasMismatch = types.indices.any { i ->
            convertTypeToWASM(types[i]) != convertTypeToWASM(stack[i + offset].jvmType)
        }
        assertFalse(hasMismatch) { "Expected $types for $funcName, got $stack" }
        val result = ArrayList<StackElement>(types.size)
        for (ti in types.lastIndex downTo 0) {
            result.add(popElement(types[ti]))
        }
        result.reverse()
        return result
    }

    private fun popElement(type: String): StackElement {
        val i0 = stack.removeLastOrNull()
            ?: assertFail("Tried popping $type, but stack was empty")
        // println("pop -> $i0 + $stack")
        assertEquals(convertTypeToWASM(type), convertTypeToWASM(i0.jvmType)) { "pop($type) vs $stack + $i0" }
        return i0
    }

    private fun push(type: String, name: String) {
        pushWithNames(VariableExpr(name, type), listOf(name), false)
    }

    private fun joinDependencies(a: StackElement, b: StackElement): List<String> {
        return (a.dependencies + b.dependencies).distinct()
    }

    private fun pushConstant(expression: Expr) {
        pushWithNames(expression, emptyList(), false)
    }

    private fun pushWithNames(expression: Expr, names: List<String>, isBoolean: Boolean) {
        stack.add(StackElement(expression, names, isBoolean))
        // println("push -> $stack")
    }

    private fun beginSetPoppedEnd(name: String, type: String) {
        append(Assignment(name, popElement(type)))
    }

    private fun handleSpecialCall(funcName: String, params: List<String>, results: List<String>): Boolean {
        unused(results)
        when {
            funcName.startsWith("getNth_") -> {
                stack.add(stack[stack.size - params.size])
            }
            funcName == "wasStaticInited" -> {
                popElement(i32)
                stack.add(zeroForStaticInited)
            }
            !enableCppTracing && (funcName == "stackPush" || funcName == "stackPop") -> {
                if (funcName == "stackPush") popElement(i32)
            }
            funcName.startsWith("swap") -> {
                stack.add(stack.size - 2, stack.removeLast())
            }
            funcName.startsWith("dupi") || funcName.startsWith("dupf") -> {
                stack.add(stack.last())
            }
            funcName.startsWith("dup2i") || funcName.startsWith("dup2f") -> {
                val v0 = stack[stack.size - 2]
                val v1 = stack.last()
                stack.add(v0)
                stack.add(v1)
            }
            // todo incorrect, because it causes a segfault :/
            false && funcName.startsWith("dup_x1") -> {
                val v1 = stack.removeLast()
                val v0 = stack.removeLast()
                stack.add(v1)
                stack.add(v0)
                stack.add(v1)
            }
            // todo incorrect, because it causes a segfault :/
            false && funcName.startsWith("dup_x2") -> {
                val v2 = stack.removeLast()
                val v1 = stack.removeLast()
                val v0 = stack.removeLast()
                stack.add(v2)
                stack.add(v0)
                stack.add(v1)
                stack.add(v2)
            }
            else -> return false
        }
        return true
    }

    private fun writeCall3(funcName: String, params: List<WASMType>, results: List<String>) {
        writeCall(funcName, params.map { it.wasmName }, results)
    }

    private fun writeCall2(funcName: String, params: List<WASMType>, results: List<WASMType>) {
        writeCall(funcName, params.map { it.wasmName }, results.map { it.wasmName })
    }

    private fun writeCall(funcName: String, params: List<String>, results: List<String>) {
        if (handleSpecialCall(funcName, params, results)) return

        val popped = popInReverse(funcName, params)
        val canInlinePureCall = results.size == 1 && funcName in pureFunctions
        when {
            results.isEmpty() -> {
                // empty function -> nothing to be inlined
                append(ExprCall(funcName, popped))
            }
            canInlinePureCall -> {
                inlinePureCall(funcName, popped, results[0])
            }
            else -> {
                val resultName = nextTemporaryVariable()
                append(CallAssignment(funcName, popped, results, resultName, null))
                writeResults(results, resultName)
            }
        }
    }

    private fun inlinePureCall(funcName: String, popped: List<StackElement>, type: String) {
        // can be inlined :)
        val joinedNames = popped.flatMap { it.dependencies }.distinct()
        stack.add(StackElement(CallExpr(funcName, popped.map { it.expr }, type), joinedNames, false))
    }

    private fun writeResults(results: List<String>, resultName: String) {
        if (results.size > 1) {
            writeMultipleResults(results, resultName)
        } else {
            push(results[0], resultName)
        }
    }

    private fun writeMultipleResults(results: List<String>, resultName: String) {
        // too complicated to be inlined for now
        val totalType = results.joinToString("")
        val resultVar = VariableExpr(resultName, totalType)
        for (j in results.indices) {
            val newName = nextTemporaryVariable()
            val typeJ = results[j]
            val field = FieldSig(totalType, "v$j", typeJ, false)
            val expr = FieldGetExpr(field, resultVar, true)
            append(Declaration(typeJ, newName, StackElement(expr, listOf(resultName), false)))
            push(typeJ, newName)
        }
    }

    private fun pushInvalidResults(results: List<String>) {
        for (i in results.indices) {
            push(results[i], "undefined")
        }
    }

    private fun writeInstructions(instructions: List<Instruction>): Boolean {
        return writeInstructions(instructions, 0, instructions.size)
    }

    private fun writeInstructions(instructions: List<Instruction>, i0: Int, i1: Int): Boolean {
        val assignments = Assignments.findAssignments(instructions, pureFunctions)
        for (i in i0 until i1) {
            val instr = instructions[i]
            writeInstruction(instr, i, assignments)
            if (instr.isReturning()) return true
        }
        return false
    }

    private fun needsNewVariable(name: String, assignments: Map<String, Int>?, i: Int): Boolean {
        return Assignments.hasAssignment(assignments, name, i)
    }

    private fun needsNewVariable(names: List<String>, assignments: Map<String, Int>?, i: Int): Boolean {
        return names.any2 { name -> needsNewVariable(name, assignments, i) }
    }

    private fun writeInstruction(i: Instruction) {
        writeInstruction(i, Int.MAX_VALUE, null)
    }

    private fun unaryInstr(
        aType: String, rType: String,
        k: Int, assignments: Map<String, Int>?, isBoolean: Boolean,
        combine: (Expr) -> Expr,
    ) {
        val a = popElement(aType)
        val newValue = combine(a.expr)
        if (a.dependencies.any2 { name -> needsNewVariable(name, assignments, k) }) {
            val newName = nextTemporaryVariable()
            append(Declaration(rType, newName, StackElement(newValue, a.dependencies, isBoolean)))
            push(rType, newName)
        } else {
            pushWithNames(newValue, a.dependencies, isBoolean)
        }
    }

    private fun binaryInstr(
        aType: String, bType: String, rType: String,
        k: Int, assignments: Map<String, Int>?, isBoolean: Boolean,
        combine: (Expr, Expr) -> Expr
    ) {
        val i1 = popElement(aType)
        val i0 = popElement(bType)
        val newValue = combine(i0.expr, i1.expr)
        val dependencies = joinDependencies(i0, i1)
        if (needsNewVariable(i0.dependencies, assignments, k) ||
            needsNewVariable(i1.dependencies, assignments, k)
        ) {
            val newName = nextTemporaryVariable()
            append(Declaration(rType, newName, StackElement(newValue, dependencies, false)))
            push(rType, newName)
        } else {
            pushWithNames(newValue, dependencies, isBoolean)
        }
    }

    private fun writeGetInstruction(
        type: String, name: String,
        k: Int, assignments: Map<String, Int>?
    ) {
        if (needsNewVariable(name, assignments, k)) {
            val newName = nextTemporaryVariable()
            val expr = VariableExpr(name, type)
            append(Declaration(type, newName, StackElement(expr, listOf(name), false)))
            push(type, newName)
        } else {
            push(type, name)
        }
    }

    private fun load(type: String, memoryType: String = type) {
        val ptr = popElement(ptrType)
        val newName = nextTemporaryVariable()
        append(CppLoadInstr(type, newName, memoryType, ptr))
        push(type, newName)
    }

    private fun store(type: String, memoryType: String = type) {
        val value = popElement(type)
        val ptr = popElement(ptrType)
        append(CppStoreInstr(type, memoryType, ptr, value))
    }

    private fun writeInstruction(i: Instruction, k: Int, assignments: Map<String, Int>?) {
        when (i) {
            is ParamGet -> {
                val index = i.index
                val type = function.params[index]
                // assertEquals(i.name, type.name) // todo why is the name incorrect???
                writeGetInstruction(type.jvmType, type.name, k, assignments)
            }
            is LocalGet -> {
                val local = localsByName[i.name]
                    ?: throw IllegalStateException("Missing local '${i.name}'")
                assertNotEquals("lbl", i.name)
                writeGetInstruction(local.jvmType, local.name, k, assignments)
            }
            is GlobalGet -> {
                val global = globals[i.name]
                    ?: throw IllegalStateException("Missing global '${i.name}'")
                writeGetInstruction(global.wasmType.wasmName, global.fullName, k, assignments)
            }
            is ParamSet -> {
                val index = i.index
                val type = function.params[index]
                // assertEquals(i.name, type.name) // todo why is the name incorrect???
                beginSetPoppedEnd(type.name, type.jvmType)
            }
            is LocalSet -> {
                val local = localsByName[i.name]
                    ?: throw IllegalStateException("Missing local '${i.name}'")
                if (i.name != "lbl") {
                    beginSetPoppedEnd(local.name, local.jvmType)
                } else {
                    // unfortunately can still happen
                    val value = popElement(i32).expr
                    append(Comment("// skipping lbl = $value"))
                }
            }
            is GlobalSet -> {
                val global = globals[i.name]
                    ?: throw IllegalStateException("Missing global '${i.name}'")
                beginSetPoppedEnd(global.fullName, global.wasmType.wasmName)
            }
            // loading and storing
            // loading and storing
            I32Load8S -> load(i32, "int8_t")
            I32Load8U -> load(i32, "uint8_t")
            I32Load16S -> load(i32, "int16_t")
            I32Load16U -> load(i32, "uint16_t")
            I32Load -> load(i32)
            I64Load -> load(i64)
            F32Load -> load(f32)
            F64Load -> load(f64)
            // storing
            I32Store8 -> store(i32, "int8_t")
            I32Store16 -> store(i32, "int16_t")
            I32Store -> store(i32)
            I64Store -> store(i64)
            F32Store -> store(f32)
            F64Store -> store(f64)
            // other operations
            is EqualsZeroInstruction -> unaryInstr(i.popType, i.pushType, k, assignments, true) {
                UnaryExpr(i, it, "boolean")
            }
            is ShiftInstr -> binaryInstr(i.popType, i.popType, i.pushType, k, assignments, false) { i0, i1 ->
                BinaryExpr(i, i0, i1, i.pushType)
            }
            Return -> {
                val results = function.results
                append(ExprReturn(popInReverse(function.funcName, results)))
            }
            Unreachable -> append(i)
            is Const -> {
                // will be integrated into expressions
                pushConstant(ConstExpr(i.value, i.type.wasmName))
            }
            is StringConst -> {
                // will be integrated into expressions
                if (useHighLevelMemoryAccess) {
                    pushConstant(ConstExpr(i.string, "java/lang/String"))
                } else {
                    pushConstant(ConstExpr(i.address, "java/lang/String"))
                }
            }
            is UnaryFloatInstruction -> unaryInstr(i.popType, i.pushType, k, assignments, false) { expr ->
                UnaryExpr(i, expr, i.pushType)
            }
            is NumberCastInstruction -> unaryInstr(i.popType, i.pushType, k, assignments, false) { expr ->
                UnaryExpr(i, expr, i.pushType)
            }
            is BinaryInstruction -> binaryInstr(i.popType, i.popType, i.pushType, k, assignments, false) { i0, i1 ->
                BinaryExpr(i, i0, i1, i.pushType)
            }
            is IfBranch -> {

                // get running parameters...
                val condition = popElement(i32)
                val baseSize = stack.size - i.params.size

                // confirm parameters to branch are correct
                for (j in i.params.indices) {
                    assertEquals(convertTypeToWASM(stack[baseSize + j].jvmType), convertTypeToWASM(i.params[j]))
                }

                // only pop them, if their content is complicated expressions
                // then only replace specifically those in the stack
                for (j in i.params.indices) {
                    val j1 = baseSize + j
                    val stackJ = stack[j1]
                    if (!isNameOrNumber(stackJ.expr)) {
                        val newName = nextTemporaryVariable()
                        append(Declaration(stackJ.jvmType, newName, stackJ))
                        val expr = VariableExpr(newName, stackJ.jvmType)
                        stack[j1] = StackElement(expr, listOf(newName), false)
                    }
                }
                /*val paramPopped = popInReverse(function.funcName, i.params)
                for (j in i.params.indices) {
                    beginNew(i.params[j]).append(paramPopped[j]).end()
                }*/

                if (i.ifFalse.isEmpty()) {
                    assertEquals(i.params.size, i.results.size) {
                        "Invalid Branch: $i"
                    }
                }

                val needsResults = !i.isReturning()
                val resultVars = if (needsResults) {
                    i.results.map { nextTemporaryVariable() }
                } else emptyList()
                if (needsResults) for (ri in resultVars.indices) {
                    append(NullDeclaration(i.results[ri], resultVars[ri]))
                }

                val stackSave = ArrayList(stack)
                stackSave.subList(baseSize, stackSave.size).clear()
                stack.subList(0, baseSize).clear()
                val stackForReset = ArrayList(stack)

                fun packResultsIntoOurStack(instructions: List<Instruction>) {
                    val lastInstr = instructions.lastOrNull()
                    if (lastInstr == Return || lastInstr == Unreachable || lastInstr is Jump) {
                        return
                    }
                    // pack results into our stack somehow...
                    // resultVars[i] = stack[i].name
                    if (needsResults) {
                        for (j in i.results.lastIndex downTo 0) {
                            val srcVar = popElement(i.results[j])
                            val dstVar = resultVars[j]
                            append(Assignment(dstVar, srcVar))
                        }
                    }
                }

                fun writeBranchContents(instructions: List<Instruction>) {
                    writeInstructions(instructions)
                    packResultsIntoOurStack(instructions)
                }

                val conditionExpr = condition.expr
                if (conditionExpr is ConstExpr && conditionExpr.value is Boolean) {
                    val value = conditionExpr.value
                    writeBranchContents(if (value) i.ifTrue else i.ifFalse)
                } else if (conditionExpr is ConstExpr && conditionExpr.value is Int) {
                    val value = conditionExpr.value.toInt()
                    writeBranchContents(if (value != 0) i.ifTrue else i.ifFalse)
                } else {
                    val newIfTrue = ArrayList<Instruction>()
                    writer.add(newIfTrue)

                    writeBranchContents(i.ifTrue)

                    writer.removeLast()
                    val newIfFalse = ArrayList<Instruction>()
                    writer.add(newIfFalse)

                    // reset stack to before "if"
                    stack.clear()
                    stack.addAll(stackForReset)

                    writeBranchContents(i.ifFalse)

                    writer.removeLast()
                    append(ExprIfBranch(condition, newIfTrue, newIfFalse))
                }

                // rescue stack
                stack.clear()
                stack.addAll(stackSave)
                if (needsResults) {
                    for (j in resultVars.indices) {
                        push(i.results[j], resultVars[j])
                    }
                } else {
                    pushInvalidResults(i.results)
                }
            }
            is Call -> {
                // todo function calls can be inlined into expressions, if they aren't native, and they don't write (recursively)
                //  -> find these methods, so we can inline them
                val func = functionsByName[i.name]
                    ?: throw IllegalStateException("Missing ${i.name}")
                writeCall3(func.funcName, func.params.map { it.wasmType }, func.results)
            }
            is CallIndirect -> {
                val type = i.type
                val tmpType = nextTemporaryVariable()
                val tmpVar = nextTemporaryVariable()
                append(FunctionTypeDefinition(type, tmpType, tmpVar, popElement(i32)))
                writeCall2(tmpVar, type.params, type.results)
            }
            is LoopInstr -> {
                val resultNames = i.results.map { nextTemporaryVariable() }
                for (j in i.results.indices) {
                    append(NullDeclaration(i.results[j], resultNames[j]))
                }

                val newLoop = ArrayList<Instruction>()
                writer.add(newLoop)

                val stackSave = ArrayList(stack)
                stack.clear()
                val lastIsContinue = (i.body.lastOrNull() as? Jump)?.label == i.label
                val i1 = i.body.size - lastIsContinue.toInt()
                writeInstructions(i.body, 0, i1)

                if (!lastIsContinue) {
                    // save results
                    for (j in i.results.lastIndex downTo 0) {
                        append(Assignment(resultNames[j], popElement(i.results[j])))
                    }
                    append(BreakThisLoopInstr)
                } else assertTrue(i.results.isEmpty())

                stack.clear()
                stack.addAll(stackSave)
                for (j in i.results.indices) {
                    push(i.results[j], resultNames[j])
                }

                writer.removeLast()
                append(LoopInstr(i.label, newLoop, i.params, i.results))
            }
            is Jump -> {
                // C++ doesn't have proper continue@label/break@label, so use goto
                append(GotoInstr(i.label))
            }
            is JumpIf -> {
                // C++ doesn't have proper continue@label/break@label, so use goto
                val condition = popElement("i32")
                append(ExprIfBranch(condition, arrayListOf(GotoInstr(i.label)), emptyArrayList))
            }
            Drop -> stack.pop()
            is Comment -> append(i)
            PtrDupInstr -> stack.add(stack.last())
            is FieldGetInstr -> {
                if (useHighLevelMemoryAccess) {
                    val self = if (!i.fieldSig.isStatic) popElement(i.fieldSig.clazz) else null
                    val dependencies = self?.dependencies ?: emptyList()
                    val isBoolean = i.fieldSig.jvmType == "boolean"
                    stack.add(StackElement(FieldGetExpr(i.fieldSig, self?.expr, false), dependencies, isBoolean))
                } else writeInstructions(i.toLowLevel())
            }
            is FieldSetInstr -> {
                if (useHighLevelMemoryAccess) {
                    if (i.reversed) {
                        val self = popElement(i.fieldSig.clazz)
                        val value = popElement(i.fieldSig.jvmType)
                        append(FieldAssignment(i.fieldSig, self, value))
                    } else {
                        val value = popElement(i.fieldSig.jvmType)
                        val self = if (!i.fieldSig.isStatic) popElement(i.fieldSig.clazz) else null
                        append(FieldAssignment(i.fieldSig, self, value))
                    }
                } else writeInstructions(i.toLowLevel())
            }
            is InvokeMethodInstr -> {
                if (useHighLevelMethodResolution) {
                    appendHighLevelCall(i)
                } else writeInstructions(i.toLowLevel())
            }
            is HighLevelInstruction -> writeInstructions(i.toLowLevel())
            else -> assertFail("Unknown instruction type ${i.javaClass}")
        }
    }

    private fun mustWriteResolvedMethod(sig: MethodSig): Boolean {
        var className = sig.className
        if (className.startsWith("[") && className !in NativeTypes.nativeArrays) className = "[]"
        if (getAlias(sig) != sig && className !in gIndex.classIndex) {
            assertTrue(hIndex.isStatic(sig)) {
                "${sig.className} is unknown, but also $sig is used and not static"
            }
            // traditional method resolving, kind of hacky...
            return true
        } else return false
    }

    private fun appendHighLevelCall(i: InvokeMethodInstr) {

        // stackPush
        val stackId = i.stackPushId
        if (stackId >= 0) {
            val constExpr = ConstExpr(stackId, "int")
            val constStack = StackElement(constExpr, emptyList(), false)
            append(ExprCall(Call.stackPush.name, listOf(constStack)))
        }

        var sig = i.original
        if (mustWriteResolvedMethod(sig)) sig = hIndex.getAlias(sig)

        // actual call
        val isStatic = hIndex.isStatic(sig)
        val expectStatic = i is InvokeStaticInstr
        assertEquals(expectStatic, isStatic)

        val params = sig.descriptor.params
        val results = sig.descriptor.getResultTypes(canThrowError(sig))

        val tmpFuncName = methodName(sig)

        if (i is ResolvedMethodInstr) {
            // self for resolution isn't needed
            val isSpecial = i is InvokeSpecialInstr
            val popped = popInReverse(tmpFuncName, params)
            val self = if (isStatic) null else popElement(sig.className)
            if (results.isEmpty()) {
                append(UnresolvedExprCall(self, sig, isSpecial, popped))
            } else {
                val resultName = nextTemporaryVariable()
                append(UnresolvedCallAssignment(self, sig, isSpecial, popped, results, resultName, null))
                writeResults(results, resultName)
            }
        } else {
            assertFalse(isStatic)

            i as UnresolvedMethodInstr
            val isSpecial = false
            val selfForResolution = popElement(sig.className)
            val popped = popInReverse(tmpFuncName, params)
            val self = popElement(sig.className)
            assertEquals(self, selfForResolution)
            if (results.isEmpty()) {
                append(UnresolvedExprCall(self, sig, isSpecial, popped))
            } else {
                val resultName = nextTemporaryVariable()
                append(UnresolvedCallAssignment(self, sig, isSpecial, popped, results, resultName, null))
                writeResults(results, resultName)
            }
        }

        // stackPop
        if (stackId >= 0) {
            append(ExprCall(Call.stackPop.name, emptyList()))
        }
    }
}
