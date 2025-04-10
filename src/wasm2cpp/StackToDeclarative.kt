package wasm2cpp

import highlevel.HighLevelInstruction
import highlevel.PtrDupInstr
import me.anno.utils.Warning.unused
import me.anno.utils.assertions.*
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.structures.lists.Lists.pop
import me.anno.utils.types.Booleans.toInt
import org.apache.logging.log4j.LogManager
import translator.JavaTypes.convertTypeToWASM
import utils.StringBuilder2
import utils.WASMType
import utils.WASMTypes.*
import utils.ptrType
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
import wasm2cpp.instr.*

/**
 * Converts stack-based style from WASM into the typical declarative style with inline expressions
 * */
class StackToDeclarative(
    val globals: Map<String, GlobalVariable>,
    private val functionsByName: Map<String, FunctionImpl>,
    private val pureFunctions: Set<String>
) {

    companion object {
        private val LOGGER = LogManager.getLogger(StackToDeclarative::class)

        private const val SYMBOLS = "+-*/:&|%<=>!"
        private val zeroForStaticInited = StackElement(i32, "0", emptyList(), true)

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

        private fun isNameOrNumber(expression: String): Boolean {
            return expression.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '.' } ||
                    expression.toDoubleOrNull() != null
        }

        private fun isNumber(expression: String): Boolean {
            for (i in expression.indices) {
                val char = expression[i]
                when (char) {
                    in '0'..'9' -> {} // ok
                    // difficult -> just use built-in, even if a little slow
                    '+', '-', 'e', 'E' -> return expression.toDoubleOrNull() != null
                    else -> return false
                }
            }
            return true // all digits -> a number
        }

        fun StringBuilder2.appendExpr(expression: StackElement): StringBuilder2 {
            if (isNameOrNumber(expression.expr)) {
                append(expression.expr)
            } else {
                append('(').append(expression.expr).append(')')
            }
            return this
        }
    }

    private var localsByName: Map<String, LocalVariable> = emptyMap()
    private val tmpExprBuilder = StringBuilder2()

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
            convertTypeToWASM(types[i]) != convertTypeToWASM(stack[i + offset].type)
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
        assertEquals(convertTypeToWASM(type), convertTypeToWASM(i0.type)) { "pop($type) vs $stack + $i0" }
        return i0
    }

    private fun push(type: String, name: String) {
        val names = listOf(name)
        pushWithNames(type, name, names, false)
    }

    private fun joinNames(a: StackElement, b: StackElement): List<String> {
        return (a.names + b.names).distinct()
    }

    private fun pushConstant(type: WASMType, expression: String) {
        pushWithNames(type.wasmName, expression, emptyList(), false)
    }

    private fun pushWithNames(type: String, expression: String, names: List<String>, isBoolean: Boolean) {
        stack.add(StackElement(type, expression, names, isBoolean))
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

    private fun inlineCall(funcName: String, popped: List<StackElement>): String {
        val tmp = tmpExprBuilder
        tmp.append(funcName).append('(')
        for (i in popped.indices) {
            if (i > 0) tmp.append(", ")
            tmp.append(popped[i].expr)
        }
        tmp.append(')')
        val inlinedCall = tmp.toString()
        tmp.clear()
        return inlinedCall
    }

    private fun writeCall3(funcName: String, params: List<WASMType>, results: List<String>) {
        writeCall(funcName, params.map { it.wasmName }, results)
    }

    private fun writeCall2(funcName: String, params: List<WASMType>, results: List<WASMType>) {
        writeCall(funcName, params.map { it.wasmName }, results.map { it.wasmName })
    }

    private fun writeCall(funcName: String, params: List<String>, results: List<String>) {
        if (handleSpecialCall(funcName, params, results)) return
        if (results.isEmpty()) {
            // empty function -> nothing to be inlined
            val popped = popInReverse(funcName, params)
            append(ExprCall(funcName, popped, results, null, null))
            return
        }
        val popped = popInReverse(funcName, params)
        if (results.size == 1 && funcName in pureFunctions) {
            // can be inlined :)
            val type = results[0]
            val joinedNames = popped.flatMap { it.names }.distinct()
            stack.add(StackElement(type, inlineCall(funcName, popped), joinedNames, false))
            return
        }

        val resultName = nextTemporaryVariable()
        append(ExprCall(funcName, popped, results, resultName, null))
        if (results.size == 1) {
            push(results[0], resultName)
            return
        }

        // too complicated to be inlined for now
        for (j in results.indices) {
            val newName = nextTemporaryVariable()
            val typeJ = results[j]
            append(Declaration(typeJ, newName, StackElement(typeJ, "$resultName.v$j", listOf(resultName), false)))
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
        combine: (StackElement, StringBuilder2) -> Unit,
    ) {
        val a = popElement(aType)
        val tmp = tmpExprBuilder
        combine(a, tmp)
        val newValue = tmp.toString()
        if (a.names.any2 { name -> needsNewVariable(name, assignments, k) }) {
            val newName = nextTemporaryVariable()
            append(Declaration(rType, newName, StackElement(rType, newValue, a.names, isBoolean)))
            push(rType, newName)
        } else {
            pushWithNames(rType, newValue, a.names, isBoolean)
        }
        tmp.clear()
    }

    private fun binaryInstr(
        aType: String, bType: String, rType: String,
        k: Int, assignments: Map<String, Int>?, isBoolean: Boolean,
        combine: (StackElement, StackElement, StringBuilder2) -> Unit
    ) {
        val i1 = popElement(aType)
        val i0 = popElement(bType)
        val tmp = tmpExprBuilder
        combine(i0, i1, tmp)
        val newValue = tmp.toString()
        val dependencies = joinNames(i0, i1)
        if (needsNewVariable(i0.names, assignments, k) ||
            needsNewVariable(i1.names, assignments, k)
        ) {
            val newName = nextTemporaryVariable()
            append(Declaration(rType, newName, StackElement(rType, newValue, dependencies, false)))
            push(rType, newName)
        } else {
            pushWithNames(rType, newValue, dependencies, isBoolean)
        }
        tmp.clear()
    }

    private fun writeGetInstruction(
        type: String, name: String,
        k: Int, assignments: Map<String, Int>?
    ) {
        if (needsNewVariable(name, assignments, k)) {
            val newName = nextTemporaryVariable()
            append(Declaration(type, newName, StackElement(type, name, listOf(name), false)))
            push(type, newName)
        } else {
            push(type, name)
        }
    }

    private fun extractSymbolsFromExpression(expr: String): String {
        val builder = StringBuilder2()
        var depth = 0
        for (char in expr) {
            // if '(', skip until ')'
            if (char == '(') depth++
            if (char == ')') depth--
            if (depth == 0) {
                if (char in SYMBOLS) {
                    builder.append(char)
                }
            }
        }
        return builder.toString()
    }

    private fun canAppendWithoutBrackets(expr: String, symbol: String, isLeft: Boolean): Boolean {
        when (symbol) {
            "+" -> {
                val symbols = extractSymbolsFromExpression(expr)
                return symbols.all { it in "*/+-" }
            }
            "-" -> {
                val symbols = extractSymbolsFromExpression(expr)
                return symbols.all { it in "*/" || (it == '+' && isLeft) }
            }
            "*" -> {
                val symbols = extractSymbolsFromExpression(expr)
                return symbols.all { it == '*' }
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
            "|" -> {
                val symbols = extractSymbolsFromExpression(expr)
                return symbols.all { it == '|' }
            }
            else -> return false
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
            is EqualsZeroInstruction -> unaryInstr(i.popType, i.pushType, k, assignments, true) { expr, dst ->
                if (expr.isBoolean) {
                    dst.append('!').appendExpr(expr)
                } else {
                    dst.appendExpr(expr).append(" == 0")
                }
            }
            is ShiftInstr -> {
                binaryInstr(i.popType, i.popType, i.pushType, k, assignments, false) { i0, i1, dst ->
                    val needsCast = i.isRight && i.isUnsigned
                    if (needsCast) {
                        dst.append(if (i.popType == i32) "(i32)((u32) " else "(i64)((u64) ")
                    }
                    val operator = if (i.isRight) " >> " else " << "
                    dst.appendExpr(i0).append(operator).appendExpr(i1)
                    if (needsCast) {
                        dst.append(')')
                    }
                }
            }
            Return -> {
                val results = function.results
                append(ExprReturn(popInReverse(function.funcName, results)))
            }
            Unreachable -> append(i)
            is Const -> {
                // will be integrated into expressions
                when (i.type) {
                    ConstType.F32 -> pushConstant(i.type, i.value.toString() + "f")
                    ConstType.F64 -> pushConstant(i.type, i.value.toString())
                    ConstType.I32 -> {
                        val v =
                            if (i.value == Int.MIN_VALUE) "(i32)(1u << 31)"
                            else i.value.toString()
                        pushConstant(i.type, v)
                    }
                    ConstType.I64 -> {
                        val v =
                            if (i.value == Long.MIN_VALUE) "(i64)(1llu << 63)"
                            else i.value.toString() + "ll"
                        pushConstant(i.type, v)
                    }
                }
            }
            is UnaryFloatInstruction -> unaryInstr(i.popType, i.pushType, k, assignments, false) { expr, dst ->
                dst.append(i.call).append('(').append(expr.expr).append(')')
            }
            is NumberCastInstruction -> unaryInstr(i.popType, i.pushType, k, assignments, false) { expr, dst ->
                dst.append(i.prefix).append(expr.expr).append(i.suffix)
            }
            is CompareInstr -> {
                binaryInstr(i.type, i.type, i32, k, assignments, true) { i0, i1, dst ->
                    // prevent Yoda-speach: if the first is a number, but the second isn't, swap them around
                    if (isNumber(i0.expr) && !isNumber(i1.expr)) {
                        // flipped
                        if (i.castType != null) dst.append('(').append(i.castType).append(") ")
                        dst.appendExpr(i1).append(' ').append(i.flipped).append(' ')
                        if (i.castType != null) dst.append('(').append(i.castType).append(") ")
                        dst.appendExpr(i0)
                    } else {
                        if (i.castType != null) dst.append('(').append(i.castType).append(") ")
                        if (canAppendWithoutBrackets(i0.expr, i.operator, true)) dst.append(i0.expr)
                        else dst.appendExpr(i0)
                        dst.append(' ').append(i.operator).append(' ')
                        if (i.castType != null) dst.append('(').append(i.castType).append(") ")
                        if (canAppendWithoutBrackets(i1.expr, i.operator, false)) dst.append(i1.expr)
                        else dst.appendExpr(i1)
                    }
                }
            }
            is BinaryInstruction -> binaryInstr(
                i.popType, i.popType, i.pushType, k, assignments, false
            ) { i0, i1, dst ->
                if (i.cppOperator.endsWith("(")) {
                    if (i.cppOperator.startsWith("std::rot")) {
                        dst.append(i.cppOperator)
                            .append(if (i.popType == i32) "(u32) " else "(u64) ") // cast to unsigned required
                            .append(i0.expr).append(", ").append(i1.expr).append(')')
                    } else {
                        dst.append(i.cppOperator) // call(i1, i0)
                            .append(i0.expr).append(", ").append(i1.expr).append(')')
                    }
                } else {
                    if (canAppendWithoutBrackets(i0.expr, i.cppOperator, true)) dst.append(i0.expr)
                    else dst.appendExpr(i0)
                    dst.append(' ').append(i.cppOperator).append(' ')
                    if (canAppendWithoutBrackets(i1.expr, i.cppOperator, false)) dst.append(i1.expr)
                    else dst.appendExpr(i1)
                }
            }
            is IfBranch -> {

                // get running parameters...
                val condition = popElement(i32)
                val baseSize = stack.size - i.params.size

                // confirm parameters to branch are correct
                for (j in i.params.indices) {
                    assertEquals(convertTypeToWASM(stack[baseSize + j].type), convertTypeToWASM(i.params[j]))
                }

                // only pop them, if their content is complicated expressions
                // then only replace specifically those in the stack
                for (j in i.params.indices) {
                    val j1 = baseSize + j
                    val stackJ = stack[j1]
                    if (!isNameOrNumber(stackJ.expr)) {
                        val newName = nextTemporaryVariable()
                        append(Declaration(stackJ.type, newName, stackJ))
                        stack[j1] = StackElement(stackJ.type, newName, listOf(newName), false)
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

                when (condition.expr) {
                    "1" -> writeBranchContents(i.ifTrue)
                    "0" -> writeBranchContents(i.ifFalse)
                    else -> {
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
            is HighLevelInstruction -> writeInstructions(i.toLowLevel())
            else -> assertFail("Unknown instruction type ${i.javaClass}")
        }
    }
}
