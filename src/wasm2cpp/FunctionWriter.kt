package wasm2cpp

import me.anno.utils.assertions.*
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.structures.lists.Lists.pop
import me.anno.utils.types.Booleans.toInt
import org.apache.logging.log4j.LogManager
import utils.StringBuilder2
import utils.WASMTypes.*
import wasm.instr.*
import wasm.instr.Instructions.F32Load
import wasm.instr.Instructions.F32Store
import wasm.instr.Instructions.F64Load
import wasm.instr.Instructions.F64Store
import wasm.instr.Instructions.I32EQ
import wasm.instr.Instructions.I32EQZ
import wasm.instr.Instructions.I32GES
import wasm.instr.Instructions.I32GTS
import wasm.instr.Instructions.I32LES
import wasm.instr.Instructions.I32LTS
import wasm.instr.Instructions.I32Load
import wasm.instr.Instructions.I32Load16S
import wasm.instr.Instructions.I32Load16U
import wasm.instr.Instructions.I32Load8S
import wasm.instr.Instructions.I32Load8U
import wasm.instr.Instructions.I32NE
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

// todo inherit from this class and...
//  - using HighLevel getters, setters and local-variables, pass around true structs
//  - using that, generate JavaScript

// todo enable all warnings, and clear them all for truly clean code
//  - ignore not-used outputs from functions
//  - mark functions as pure (compile-time constant)
//  - inline pure functions (incl. potential reordering) into expressions
//  - discard unused expressions

class FunctionWriter(
    val globals: Map<String, GlobalVariable>,
    private val functionsByName: Map<String, FunctionImpl>
) {

    companion object {
        private val LOGGER = LogManager.getLogger(FunctionWriter::class)

        val cppKeywords = (
                "alignas,alignof,and,and_eq,asm,atomic_cancel,atomic_commit,atomic_noexcept,auto,bitand,bitor,bool,break," +
                        "case,catch,char,char8_t,char16_t,char32_t,class,compl,concept,const,consteval,constexpr,constinit," +
                        "const_cast,continue,contract_assert,co_await,co_return,co_yield,,decltype,default,delete,do," +
                        "double,dynamic_cast,else,enum,explicit,export,extern,false,float,for,friend,goto,if,inline,int," +
                        "long,mutable,namespace,new,noexcept,not,not_eq,nullptr,operator,or,or_eq,private,protected,public," +
                        "reflexpr,register,reinterpret_cast,requires,return,short,signed,sizeof,static,static_assert," +
                        "static_cast,struct,switch,synchronized,template,this,thread_local,throw,true,try,typedef," +
                        "typeid,typename,union,unsigned,using,virtual,void,volatile,wchar_t,while,xor,xor_eq"
                ).split(',').toHashSet()

        private const val SYMBOLS = "+-*/:&|%<=>!"
    }

    private var depth = 1
    private var localsByName: Map<String, LocalVariable> = emptyMap()
    private val tmpExprBuilder = StringBuilder2()

    private val stack = ArrayList<StackElement>()
    private var genI = 0

    lateinit var function: FunctionImpl

    private fun init(function: FunctionImpl) {
        this.function = function
        depth = 1
        localsByName = function.locals
            .associateBy { it.name }
        stack.clear()
        genI = 0
    }

    fun write(function: FunctionImpl) {

        init(function)

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
        for (local in function.locals) {
            if (local.name == "lbl") continue
            begin().append(local.type).append(' ').append(local.name).append(" = 0").end()
        }
        // find first instruction worth writing:
        // we can skip all instructions just setting local variables to zero at the start, because we do that anyway
        var startI = 0
        val body = function.body
        while (startI + 1 < body.size &&
            body[startI] is Const && (body[startI] as Const).value.toDouble() == 0.0 &&
            body[startI + 1] is LocalSet
        ) startI += 2
        if (!writeInstructions(body, startI, body.size)) {
            LOGGER.warn("Appended return to ${function.funcName}")
            writeInstruction(Return)
        }
        writer.append("}\n")
    }

    private fun begin(): StringBuilder2 {
        for (i in 0 until depth) writer.append("  ")
        return writer
    }

    private fun StringBuilder2.end() {
        append(";\n")
    }

    private fun nextTemporaryVariable(): String = "tmp${genI++}"

    private fun popInReverse(funcName: String, types: List<String>): List<String> {
        assertTrue(stack.size >= types.size) { "Expected $types for $funcName, got $stack" }
        val hasMismatch = types.indices.any { i -> types[i] != stack[i + stack.size - types.size].type }
        assertFalse(hasMismatch) { "Expected $types for $funcName, got $stack" }
        val result = ArrayList<String>(types.size)
        for (ti in types.lastIndex downTo 0) {
            val name = pop(types[ti])
            result.add(name)
        }
        result.reverse()
        return result
    }

    private fun pop(type: String): String {
        return popElement(type).expr
    }

    private fun popElement(type: String): StackElement {
        val i0 = stack.removeLastOrNull()
            ?: assertFail("Tried popping $type, but stack was empty")
        // println("pop -> $i0 + $stack")
        assertEquals(type, i0.type) { "pop($type) vs $stack + $i0" }
        return i0
    }

    private fun isTemporaryVariable(name: String): Boolean {
        return name.startsWith("tmp")
    }

    private fun push(type: String, name: String) {
        val names = if (isTemporaryVariable(name)) emptyList() else listOf(name)
        pushWithNames(type, name, names, false)
    }

    private fun pushElements(type: String, expression: String, sources: List<StackElement>, isBoolean: Boolean) {
        val names = sources.flatMap { it.names }.distinct()
        pushWithNames(type, expression, names, isBoolean)
    }

    private fun pushConstant(type: String, expression: String) {
        pushWithNames(type, expression, emptyList(), false)
    }

    private fun pushWithNames(type: String, expression: String, names: List<String>, isBoolean: Boolean) {
        stack.add(StackElement(type, expression, names, isBoolean))
        // println("push -> $stack")
    }

    private fun pushNew(type: String): String {
        val name = nextTemporaryVariable()
        push(type, name)
        return name
    }

    private fun beginNew(type: String): StringBuilder2 {
        val name = pushNew(type)
        return begin().append(type).append(' ').append(name).append(" = ")
    }

    private fun beginSetEnd(name: String, type: String) {
        begin().append(name).append(" = ").append(pop(type)).end()
    }

    private fun load(type: String, memoryType: String = type) {
        val ptr = popElement(i32)
        beginNew(type).append("((").append(memoryType).append("*) ((uint8_t*) memory + (u32)")
            .appendExpr(ptr).append("))[0]").end()
    }

    private fun store(type: String, memoryType: String = type) {
        val value = pop(type)
        val ptr = popElement(i32)
        begin().append("((").append(memoryType).append("*) ((uint8_t*) memory + (u32)")
            .appendExpr(ptr).append("))[0] = ").append(value).end()
    }

    private fun writeCall(funcName: String, params: List<String>, results: List<String>) {

        if (funcName.startsWith("getNth_")) {
            stack.add(stack[stack.size - params.size])
            return
        }

        if (funcName == "wasStaticInited") {
            beginNew(i32).append("0").end()
            return
        }

        if (!enableCppTracing) {
            if (funcName == "stackPush" || funcName == "stackPop") {
                if (funcName == "stackPush") pop(i32)
                return
            }
        }

        if (funcName.startsWith("swap")) {
            stack.add(stack.size - 2, stack.removeLast())
            return
        }

        if (funcName.startsWith("dupi") || funcName.startsWith("dupf")) {
            stack.add(stack.last())
            return
        }

        val tmp = if (results.isNotEmpty()) nextTemporaryVariable() else ""
        begin()
        if (results.isNotEmpty()) {
            for (i in results.indices) {
                writer.append(results[i])
            }
            writer.append(" ").append(tmp).append(" = ")
        }

        writePureCall(funcName, params)
        writer.end()

        when (results.size) {
            0 -> {}
            1 -> push(results[0], tmp)
            else -> {
                for (j in results.indices) {
                    beginNew(results[j]).append(tmp).append(".v").append(j).end()
                }
            }
        }
    }

    private fun writePureCall(funcName: String, params: List<String>) {
        writer.append(funcName).append('(')
        val popped = popInReverse(funcName, params)
        for (i in popped.indices) {
            if (!writer.endsWith("(")) writer.append(", ")
            writer.append(popped[i])
        }
        writer.append(')')
    }

    private fun pushInvalidResults(results: List<String>) {
        for (i in results.indices) {
            push(results[i], "undefined")
        }
    }

    private fun writeInstructions(instructions: List<Instruction>): Boolean {
        return writeInstructions(instructions, 0, instructions.size)
    }

    private fun getNumReturned(call: Instruction): Int {
        return when (call) {
            is Call -> functionsByName[call.name]!!.results.size
            is CallIndirect -> call.type.results.size
            else -> -1
        }
    }

    private fun writeInstructions(instructions: List<Instruction>, i0: Int, i1: Int): Boolean {
        val assignments = Assignments.findAssignments(instructions)
        for(i in i0 until i1) {
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

    private fun StringBuilder2.appendExpr(expression: StackElement): StringBuilder2 {
        if (isNameOrNumber(expression.expr)) {
            append(expression.expr)
        } else {
            append('(').append(expression.expr).append(')')
        }
        return this
    }

    private fun unaryInstr(
        aType: String, rType: String,
        k: Int, assignments: Map<String, Int>?, isBoolean: Boolean,
        combine: (StackElement, StringBuilder2) -> Unit,
    ) {
        val a = popElement(aType)
        if (a.names.any2 { name -> needsNewVariable(name, assignments, k) }) {
            beginNew(rType)
            combine(a, writer)
            writer.end()
        } else {
            val tmp = tmpExprBuilder
            combine(a, tmp)
            val combined = tmp.toString()
            tmp.clear()
            pushWithNames(rType, combined, a.names, isBoolean)
        }
    }

    private fun binaryInstr(
        aType: String, bType: String, rType: String,
        k: Int, assignments: Map<String, Int>?, isBoolean: Boolean,
        combine: (StackElement, StackElement, StringBuilder2) -> Unit
    ) {
        val i1 = popElement(aType)
        val i0 = popElement(bType)
        if (needsNewVariable(i0.names, assignments, k) ||
            needsNewVariable(i1.names, assignments, k)
        ) {
            beginNew(rType)
            combine(i0, i1, writer)
            writer.end()
        } else {
            val tmp = tmpExprBuilder
            combine(i0, i1, tmp)
            val combined = tmp.toString()
            tmp.clear()
            pushElements(rType, combined, listOf(i0, i1), isBoolean)
        }
    }

    private fun writeGetInstruction(
        type: String, name: String,
        k: Int, assignments: Map<String, Int>?
    ) {
        if (needsNewVariable(name, assignments, k)) {
            beginNew(type).append(name).end()
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

    private fun writeInstruction(i: Instruction, k: Int, assignments: Map<String, Int>?) {
        when (i) {
            is ParamGet -> {
                val index = i.index
                val type = function.params[index]
                // assertEquals(i.name, type.name) // todo why is the name incorrect???
                writeGetInstruction(type.wasmType, type.name, k, assignments)
            }
            is LocalGet -> {
                val local = localsByName[i.name]
                    ?: throw IllegalStateException("Missing local '${i.name}'")
                assertNotEquals("lbl", i.name)
                writeGetInstruction(local.type, local.name, k, assignments)
            }
            is GlobalGet -> {
                val global = globals[i.name]
                    ?: throw IllegalStateException("Missing global '${i.name}'")
                writeGetInstruction(global.wasmType, global.fullName, k, assignments)
            }
            is ParamSet -> {
                val index = i.index
                val type = function.params[index]
                // assertEquals(i.name, type.name) // todo why is the name incorrect???
                beginSetEnd(type.name, type.wasmType)
            }
            is LocalSet -> {
                val local = localsByName[i.name]
                    ?: throw IllegalStateException("Missing local '${i.name}'")
                if (i.name != "lbl") {
                    beginSetEnd(local.name, local.type)
                } else {
                    // unfortunately can still happen
                    val value = pop(i32)
                    begin().append("// skipping lbl = $value").append('\n')
                }
            }
            is GlobalSet -> {
                val global = globals[i.name]
                    ?: throw IllegalStateException("Missing global '${i.name}'")
                beginSetEnd(global.fullName, global.wasmType)
            }
            // loading
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
                binaryInstr(i.type, i.type, i.type, k, assignments, false) { i0, i1, dst ->
                    val needsCast = i.isRight && i.isUnsigned
                    if (needsCast) {
                        dst.append(if (i.type == i32) "(i32)((u32) " else "(i64)((u64) ")
                    }
                    val operator = if (i.isRight) " >> " else " << "
                    dst.appendExpr(i0).append(operator).appendExpr(i1)
                    if (needsCast) {
                        dst.append(')')
                    }
                }
            }
            Return -> {
                val offset = stack.size - function.results.size
                assertTrue(offset >= 0) { "Missing ${-offset} return values" }
                begin().append("return")
                when (function.results.size) {
                    0 -> {}
                    1 -> writer.append(' ').append(stack[offset].expr)
                    else -> {
                        writer.append(" { ")
                        for (ri in function.results.indices) {
                            if (ri > 0) writer.append(", ")
                            writer.append(stack[ri + offset].expr)
                        }
                        writer.append(" }")
                    }
                }
                writer.end()
            }
            Unreachable -> {
                begin().append("unreachable(\"")
                    .append(function.funcName).append("\")").end()
            }
            is Const -> {
                when (i.type) {
                    ConstType.F32 -> pushConstant(i.type.wasmType, i.value.toString() + "f")
                    ConstType.F64 -> pushConstant(i.type.wasmType, i.value.toString())
                    ConstType.I32 -> {
                        val v =
                            if (i.value == Int.MIN_VALUE) "(i32)(1u << 31)"
                            else i.value.toString()
                        pushConstant(i.type.wasmType, v)
                    }
                    ConstType.I64 -> {
                        val v =
                            if (i.value == Long.MIN_VALUE) "(i64)(1llu << 63)"
                            else i.value.toString() + "ll"
                        pushConstant(i.type.wasmType, v)
                    }
                }
            }
            is UnaryFloatInstruction -> unaryInstr(i.popType, i.pushType, k, assignments, false) { expr, dst ->
                dst.append(i.call).append('(').append(expr.expr).append(')')
            }
            is NumberCastInstruction -> {
                val i0 = pop(i.popType)
                beginNew(i.pushType).append(i.prefix).append(i0).append(i.suffix).end()
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
                val condition = pop(i32)
                val baseSize = stack.size - i.params.size

                // confirm parameters to branch are correct
                for (j in i.params.indices) {
                    assertEquals(stack[baseSize + j].type, i.params[j])
                }

                // only pop them, if their content is complicated expressions
                // then only replace specifically those in the stack
                for (j in i.params.indices) {
                    val j1 = baseSize + j
                    val stackJ = stack[j1]
                    val code = stackJ.expr
                    if (!isNameOrNumber(code)) {
                        val name = nextTemporaryVariable()
                        begin().append(stackJ.type).append(' ').append(name).append(" = ")
                            .append(code).end()
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
                    begin().append(i.results[ri]).append(' ')
                        .append(resultVars[ri]).append(" = 0").end()
                }

                begin().append("if (").append(condition).append(") {\n")
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
                            val srcVar = pop(i.results[j])
                            val dstVar = resultVars[j]
                            begin().append(dstVar).append(" = ").append(srcVar).end()
                        }
                    }
                }

                fun writeBranchContents(instructions: List<Instruction>) {
                    depth++
                    writeInstructions(instructions)
                    packResultsIntoOurStack(instructions)
                    depth--
                }

                writeBranchContents(i.ifTrue)

                if (i.results.isEmpty() && i.ifFalse.isEmpty()) {
                    begin().append("}\n")
                } else {
                    begin().append("} else {\n")

                    // reset stack to before "if"
                    stack.clear()
                    stack.addAll(stackForReset)

                    writeBranchContents(i.ifFalse)

                    begin().append("}\n")
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
                val func = functionsByName[i.name]
                    ?: throw IllegalStateException("Missing ${i.name}")
                writeCall(func.funcName, func.params.map { it.wasmType }, func.results)
            }
            is CallIndirect -> {
                val type = i.type
                val tmpType = nextTemporaryVariable()
                val tmpVar = nextTemporaryVariable()
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
                    .append(tmpType).append(">(indirect[").append(pop("i32")).append("])").end()
                writeCall(tmpVar, type.params, type.results)
            }
            is BlockInstr -> {
                // write body
                writeInstructions(i.body)
                // write label at end as jump target
                begin().append(i.label).append(":\n")
            }
            is LoopInstr -> {
                val firstInstr = i.body.firstOrNull()
                if (i.body.size == 1 && firstInstr is SwitchCase &&
                    i.results.isEmpty() && stack.isEmpty()
                ) {
                    writeSwitchCase(firstInstr)
                } else {
                    val resultNames = i.results.map { nextTemporaryVariable() }
                    for (j in i.results.indices) {
                        begin().append(i.results[j]).append(' ')
                            .append(resultNames[j]).append(" = 0").end()
                    }
                    // to do check if the label is used
                    begin().append(i.label).append(": while (true) {\n")
                    val stackSave = ArrayList(stack)
                    stack.clear()
                    depth++
                    val lastIsContinue = (i.body.lastOrNull() as? Jump)?.label == i.label
                    val i1 = i.body.size - lastIsContinue.toInt()
                    writeInstructions(i.body, 0, i1)

                    if (!lastIsContinue) {
                        // save results
                        for (j in i.results.lastIndex downTo 0) {
                            begin().append(resultNames[j]).append(" = ")
                                .append(pop(i.results[j])).end()
                        }
                        begin().append("break").end()
                    } else assertTrue(i.results.isEmpty())

                    depth--
                    stack.clear()
                    stack.addAll(stackSave)
                    for (j in i.results.indices) {
                        push(i.results[j], resultNames[j])
                    }
                    begin().append("}\n")
                }
            }
            is Jump -> {
                // C++ doesn't have proper continue@label/break@label, so use goto
                begin().append("goto ").append(i.label).end()
            }
            is JumpIf -> {
                // C++ doesn't have proper continue@label/break@label, so use goto
                val condition = pop("i32")
                begin().append("if (").append(condition).append(" != 0) { goto ").append(i.label).append("; }\n")
            }
            is SwitchCase -> writeSwitchCase(i)
            Drop -> stack.pop()
            is Comment -> {
                begin().append("// ").append(i.name).append('\n')
            }
            else -> assertFail("Unknown instruction type ${i.javaClass}")
        }
    }

    /**
     * find the next instruction after i
     * */
    private fun nextInstr(instructions: List<Instruction>, i: Int): Int {
        for (j in i + 1 until instructions.size) {
            val instr = instructions[j]
            if (instr !is Comment) return j
        }
        return -1
    }

    private fun writeSwitchCase(switchCase: SwitchCase) {
        // big monster, only 1 per function allowed, afaik
        val cases = switchCase.cases
        val label = switchCase.label
        assertTrue(stack.isEmpty()) { "Expected empty stack, got $stack" }
        depth++

        fun getBranchIdx(instructions: List<Instruction>): Int {
            val first = instructions.firstOrNull()
            return if (instructions.size == 1 &&
                first is Const && first.type == ConstType.I32
            ) first.value as Int else assertFail()
        }

        for (j in cases.indices) {
            stack.clear()
            depth--
            begin().append("case").append(j).append(": {\n")
            depth++

            var hadReturn = false
            val instructions = cases[j]
            for (i in instructions.indices) {
                val ni = nextInstr(instructions, i)
                val next = instructions.getOrNull(ni)
                val instr = instructions[i]

                if (next is LocalSet && next.name == label) {
                    var nni = nextInstr(instructions, ni)
                    var nextNext = instructions[nni]
                    while (nextNext !is Jump) {
                        // confirm it is a stack variable
                        assertTrue(nextNext is LocalSet && nextNext.name.startsWith("s"))
                        val lastNNi = nni
                        nni = nextInstr(instructions, nni)
                        assertTrue(nni > lastNNi)
                        nextNext = instructions[nni]
                    }
                    fun saveStack() {
                        // save stack
                        for (k in ni + 1 until nni) {
                            writeInstruction(instructions[k])
                        }
                    }
                    when (instr) {
                        is IfBranch -> {
                            // implement branch-goto
                            val trueCase = getBranchIdx(instr.ifTrue)
                            val falseCase = getBranchIdx(instr.ifFalse)
                            val branch = pop(i32)
                            saveStack()
                            begin().append("if (").append(branch).append(") {\n")
                            begin().append("  goto case").append(trueCase).end()
                            begin().append("} else {\n")
                            begin().append("  goto case").append(falseCase).end()
                            begin().append("}\n")
                        }
                        is Const -> {
                            // implement simple goto
                            saveStack()
                            val targetCase = getBranchIdx(listOf(instr))
                            begin().append("goto case").append(targetCase).end()
                        }
                        I32EQ, I32NE, I32EQZ,
                        I32LES, I32LTS, I32GTS, I32GES -> {
                            writeInstruction(instr)
                            val branch = pop(i32)
                            begin().append("if (").append(branch).append(") {\n")
                            begin().append("  goto case1").end()
                            begin().append("} else {\n")
                            begin().append("  goto case0").end()
                            begin().append("}\n")
                        }
                        else -> throw NotImplementedError("Unknown symbol before switch-label: $instr")
                    }
                    hadReturn = true
                    break
                }

                writeInstruction(instr)

                if (instr.isReturning()) {
                    hadReturn = true
                    break
                }
            }

            assertTrue(hadReturn)

            depth--
            begin().append("}\n")
            depth++
        }
        depth--
    }
}
