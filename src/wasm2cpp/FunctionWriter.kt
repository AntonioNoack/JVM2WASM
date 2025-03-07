package wasm2cpp

import me.anno.utils.assertions.*
import me.anno.utils.structures.lists.Lists.pop
import me.anno.utils.types.Booleans.toInt
import org.apache.logging.log4j.LogManager
import utils.*
import wasm.instr.*
import wasm.instr.Instructions.Drop
import wasm.instr.Instructions.F32Load
import wasm.instr.Instructions.F32Store
import wasm.instr.Instructions.F64Load
import wasm.instr.Instructions.F64Store
import wasm.instr.Instructions.I32EQZ
import wasm.instr.Instructions.I32Load
import wasm.instr.Instructions.I32Load16S
import wasm.instr.Instructions.I32Load16U
import wasm.instr.Instructions.I32Load8S
import wasm.instr.Instructions.I32Load8U
import wasm.instr.Instructions.I32Store
import wasm.instr.Instructions.I32Store16
import wasm.instr.Instructions.I32Store8
import wasm.instr.Instructions.I64EQZ
import wasm.instr.Instructions.I64Load
import wasm.instr.Instructions.I64Store
import wasm.instr.Instructions.Return
import wasm.instr.Instructions.Unreachable
import wasm.parser.FunctionImpl
import wasm.parser.GlobalVariable

class FunctionWriter(
    val function: FunctionImpl, val globals: Map<String, GlobalVariable>,
    private val functionsByName: Map<String, FunctionImpl>
) {

    companion object {
        private val LOGGER = LogManager.getLogger(FunctionWriter::class)
    }

    private var depth = 1
    private val localsByName = function.locals
        .associateBy { it.name }

    private val stack = ArrayList<StackElement>()
    private var genI = 0

    fun write() {
        defineFunctionHead(function, true)
        writer.append(" {\n")

        if (function.funcName.startsWith("static_")) {
            writer.append("static bool wasCalled = false;\n")
            writer.append(
                if (function.results.isEmpty()) "if(wasCalled) return;\n"
                else "if(wasCalled) return 0;\n"
            )
            writer.append("wasCalled = true;\n")
        }
        for (local in function.locals) {
            if (local.name == "lbl") continue
            begin().append(local.type).append(' ').append(local.name).append(" = 0").end()
        }
        if (!writeInstructions(function.body)) {
            LOGGER.warn("Appended return")
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
        val i0 = stack.removeLastOrNull()
            ?: assertFail("Tried popping $type, but stack was empty")
        // println("pop -> $i0 + $stack")
        assertEquals(type, i0.type) { "pop($type) vs $stack + $i0" }
        return i0.name
    }

    private fun push(type: String, name: String = nextTemporaryVariable()): String {
        stack.add(StackElement(type, name))
        // println("push -> $stack")
        return name
    }

    private fun beginNew(type: String): StringBuilder2 {
        return begin().append(type).append(' ').append(push(type)).append(" = ")
    }

    private fun beginSetEnd(name: String, type: String) {
        begin().append(name).append(" = ").append(pop(type)).end()
    }

    private fun load(type: String, memoryType: String = type) {
        val ptr = pop(i32)
        beginNew(type).append("((").append(memoryType).append("*) ((uint8_t*) memory + (u32)").append(ptr)
            .append("))[0]").end()
    }

    private fun store(type: String, memoryType: String = type) {
        val value = pop(type)
        val ptr = pop(i32)
        begin().append("((").append(memoryType).append("*) ((uint8_t*) memory + (u32)").append(ptr)
            .append("))[0] = ").append(value).end()
    }

    private fun writeCall(funcName: String, params: List<String>, results: List<String>) {

        if (funcName.startsWith("getNth_")) {
            stack.add(stack[stack.size - params.size])
            return
        }

        if (funcName == "wasStaticInited") {
            beginNew("i32").append("0").end()
            return
        }

        if (!enableCppTracing) {
            if (funcName == "stackPush" || funcName == "stackPop") {
                if (funcName == "stackPush") pop("i32")
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

        writer.append(funcName).append('(')
        val popped = popInReverse(funcName, params)
        for (i in popped.indices) {
            if (!writer.endsWith("(")) writer.append(", ")
            writer.append(popped[i])
        }
        writer.append(')').end()

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

    private fun writeInstructions(instructions: List<Instruction>): Boolean {
        return writeInstructions(instructions, 0, instructions.size)
    }

    private fun writeInstructions(instructions: List<Instruction>, i0: Int, i1: Int): Boolean {
        for (i in i0 until i1) {
            val instr = instructions[i]
            writeInstruction(instr)
            if (instr.isReturning()) return true
        }
        return false
    }

    private fun writeInstruction(i: Instruction) {
        when (i) {
            is ParamGet -> {
                val index = i.index
                val type = function.params[index]
                beginNew(type).append(i.name).end()
            }
            is ParamSet -> {
                val index = i.index
                val type = function.params[index]
                beginSetEnd(i.name, type)
            }
            is GlobalGet -> {
                val global = globals[i.name]
                    ?: throw IllegalStateException("Missing global '${i.name}'")
                beginNew(global.type).append(global.name).end()
            }
            is GlobalSet -> {
                val global = globals[i.name]
                    ?: throw IllegalStateException("Missing global '${i.name}'")
                beginSetEnd(global.name, global.type)
            }
            is LocalGet -> {
                val local = localsByName[i.name]
                    ?: throw IllegalStateException("Missing local '${i.name}'")
                assertNotEquals("lbl", i.name)
                beginNew(local.type).append(local.name).end()
            }
            is LocalSet -> {
                val local = localsByName[i.name]
                    ?: throw IllegalStateException("Missing local '${i.name}'")
                if (i.name != "lbl") {
                    beginSetEnd(local.name, local.type)
                } else {
                    pop(i32)
                }
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
            I32EQZ -> {
                val i0 = pop(i32)
                beginNew(i32).append(i0).append(" == 0 ? 1 : 0").end()
            }
            I64EQZ -> {
                val i0 = pop(i64)
                beginNew(i32).append(i0).append(" == 0 ? 1 : 0").end()
            }
            is ShiftInstr -> {
                val i0 = pop(i.type)
                val i1 = pop(i.type)
                beginNew(i.type)
                writer.append(
                    if (i.isRight && i.isU) {
                        if (i.type == i32) "(u32) " else "(u64) "
                    } else ""
                )
                writer.append(i1).append(
                    if (i.isRight) " >> "
                    else " << "
                ).append(i0).end()
            }
            Return -> {
                val offset = stack.size - function.results.size
                assertTrue(offset >= 0) { "Missing ${-offset} return values" }
                begin().append("return")
                when (function.results.size) {
                    0 -> {}
                    1 -> writer.append(' ').append(stack[offset].name)
                    else -> {
                        writer.append(" { ")
                        for (ri in function.results.indices) {
                            if (ri > 0) writer.append(", ")
                            writer.append(stack[ri + offset].name)
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
                    ConstType.F32 -> push(i.type.wasmType, i.value.toString() + "f")
                    ConstType.F64 -> push(i.type.wasmType, i.value.toString())
                    ConstType.I32 -> {
                        val v =
                            if (i.value == Int.MIN_VALUE) "(i32)(1u << 31)"
                            else i.value.toString()
                        push(i.type.wasmType, v)
                    }
                    ConstType.I64 -> {
                        val v =
                            if (i.value == Long.MIN_VALUE) "(i64)(1llu << 63)"
                            else i.value.toString() + "ll"
                        push(i.type.wasmType, v)
                    }
                }
            }
            is UnaryInstruction -> {
                val i0 = pop(i.type)
                beginNew(i.type).append(i.call).append('(').append(i0).append(')').end()
            }
            is NumberCastInstruction -> {
                val i0 = pop(i.popType)
                beginNew(i.pushType).append(i.prefix).append(i0).append(i.suffix).end()
            }
            is BinaryInstruction -> {
                val i0 = pop(i.type)
                val i1 = pop(i.type)
                if (i.operator.endsWith("(")) {
                    if (i.operator.startsWith("std::rot")) {
                        beginNew(i.type).append(i.operator).append( // cast to unsigned required
                            if (i0 == i32) "(u32) " else "(u64) "
                        ).append(i1).append(", ").append(i0).append(')').end()
                    } else {
                        beginNew(i.type).append(i.operator).append(i1).append(", ")
                            .append(i0).append(')').end()
                    }
                } else {
                    beginNew(i.type).append(i1).append(' ')
                        .append(i.operator)
                        .append(' ').append(i0).end()
                }
            }
            is CompareInstr -> {
                val i0 = pop(i.type)
                val i1 = pop(i.type)
                beginNew(i32)
                if (i.castType != null) writer.append("(").append(i.castType).append(") ")
                writer.append(i1).append(' ').append(i.operator).append(' ')
                if (i.castType != null) writer.append("(").append(i.castType).append(") ")
                writer.append(i0).append(" ? 1 : 0").end()
            }
            is IfBranch -> {

                // get running parameters...
                val condition = pop(i32)
                val baseSize = stack.size - i.params.size
                val paramPopped = popInReverse(function.funcName, i.params)
                for (j in i.params.indices) {
                    beginNew(i.params[j]).append(paramPopped[j]).end()
                }

                if (i.ifFalse.isEmpty()) {
                    assertEquals(i.params.size, i.results.size) {
                        "Invalid Branch: $i"
                    }
                }

                val resultVars = i.results.map { nextTemporaryVariable() }
                for (ri in resultVars.indices) {
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
                    for (j in i.results.lastIndex downTo 0) {
                        val srcVar = pop(i.results[j])
                        val dstVar = resultVars[j]
                        begin().append(dstVar).append(" = ").append(srcVar).end()
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
                for (j in i.results.indices) {
                    push(i.results[j], resultVars[j])
                }
            }
            is Call -> {
                val func = functionsByName[i.name]
                    ?: throw IllegalStateException("Missing ${i.name}")
                writeCall(func.funcName, func.params, func.results)
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
        val lblName = switchCase.label
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

                if (next is LocalSet && next.name == lblName) {
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
                        else -> throw NotImplementedError()
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
