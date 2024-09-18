package wasm2cpp

import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.lists.Lists.pop
import me.anno.utils.types.Booleans.toInt
import utils.StringBuilder2
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
import wasm.parser.WATParser

data class StackElement(val type: String, val name: String)

fun defineFunctionHead(function: FunctionImpl, parameterNames: Boolean) {
    defineFunctionHead(function.funcName, function.params, function.results, parameterNames)
}

fun defineFunctionHead(funcName: String, params: List<String>, results: List<String>, parameterNames: Boolean) {
    if (results.isEmpty()) {
        writer.append("void")
    } else {
        for (ri in results) {
            writer.append(ri)
        }
    }
    writer.append(' ').append(funcName).append('(')
    for (i in params.indices) {
        val pi = params[i]
        if (i > 0) writer.append(", ")
        writer.append(pi)
        if (parameterNames) {
            writer.append(" p").append(i)
        }
    }
    writer.append(")")
}

fun defineFunctionImplementations(parser: WATParser) {
    writer.append("// implementations\n")
    writer.append("#include <cmath> // trunc, ...\n")
    val functions = parser.functions
    for (fi in functions.indices) {
        defineFunctionImplementation(functions[fi], parser)
    }
    writer.append('\n')
}

fun defineFunctionImplementation(function: FunctionImpl, parser: WATParser) {
    defineFunctionHead(function, true)
    writer.append(" {\n")

    var depth = 1
    val localsByName = function.locals
        .associateBy { it.name }

    fun begin(): StringBuilder2 {
        for (i in 0 until depth) {
            writer.append("  ")
        }
        return writer
    }

    fun StringBuilder2.end() {
        append(";\n")
    }

    val stack = ArrayList<StackElement>()
    var genI = 0
    fun gen(): String = "tmp${genI++}"
    fun pop(type: String): String {
        val i0 = stack.removeLast()
        // println("pop -> $i0 + $stack")
        assertEquals(type, i0.type)
        return i0.name
    }

    fun push(type: String, name: String = gen()): String {
        stack.add(StackElement(type, name))
        // println("push -> $stack")
        return name
    }

    fun beginNew(type: String): StringBuilder2 {
        return begin().append(type).append(' ').append(push(type)).append(" = ")
    }

    fun beginSetEnd(name: String, type: String) {
        begin().append(name).append(" = ").append(pop(type)).end()
    }

    fun shift(type: String): Int {
        return when (type) {
            "i32", "f32" -> 2
            "i64", "f64" -> 3
            else -> throw NotImplementedError()
        }
    }

    fun load(type: String, memoryType: String = type) {
        val ptr = pop("i32")
        beginNew(type).append("((").append(memoryType).append("*) memory)[(u32) ").append(ptr)
            .append(" >> ").append(shift(type)).append("]").end()
    }

    fun store(type: String, memoryType: String = type) {
        val value = pop(type)
        val ptr = pop("i32")
        begin().append("((").append(memoryType).append("*) memory)[(u32) ").append(ptr)
            .append(" >> ").append(shift(type)).append("] = ").append(value).append(";\n")
    }

    fun writeCall(funcName: String, params: List<String>, results: List<String>) {

        if (funcName.startsWith("getNth_")) {
            stack.add(stack[stack.size - params.size])
            return
        }

        if (funcName == "stackPush" || funcName == "stackPop") {
            if (funcName == "stackPush") pop("i32")
            return
        }

        if (funcName.startsWith("swap")) {
            stack.add(stack.size - 2, stack.removeLast())
            return
        }

        val tmp = if (results.isNotEmpty()) gen() else ""
        begin()
        if (results.isNotEmpty()) {
            for (ri in results.indices) {
                writer.append(results[ri])
            }
            writer.append(" ").append(tmp).append(" = ")
        }

        writer.append(funcName).append('(')
        val popped = params.reversed().map { pop(it) }
        for (pi in popped.reversed()) {
            if (!writer.endsWith("(")) writer.append(", ")
            writer.append(pi)
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

    fun writeInstruction(i: Instruction) {
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
                val global = parser.globals[i.name]!!
                beginNew(global.type).append(global.name).end()
            }
            is GlobalSet -> {
                val global = parser.globals[i.name]!!
                beginSetEnd(global.name, global.type)
            }
            is LocalGet -> {
                val local = localsByName[i.name]!!
                beginNew(local.type).append(local.name).end()
            }
            is LocalSet -> {
                val local = localsByName[i.name]!!
                beginSetEnd(local.name, local.type)
            }
            // loading
            I32Load8S -> load("i32", "int8_t")
            I32Load8U -> load("i32", "uint8_t")
            I32Load16S -> load("i32", "int16_t")
            I32Load16U -> load("i32", "uint16_t")
            I32Load -> load("i32")
            I64Load -> load("i64")
            F32Load -> load("f32")
            F64Load -> load("f64")
            // storing
            I32Store8 -> store("i32", "int8_t")
            I32Store16 -> store("i32", "int16_t")
            I32Store -> store("i32")
            I64Store -> store("i64")
            F32Store -> store("f32")
            F64Store -> store("f64")
            // other operations
            I32EQZ -> {
                val i0 = pop("i32")
                beginNew("i32").append(i0).append(" == 0 ? 1 : 0").end()
            }
            I64EQZ -> {
                val i0 = pop("i64")
                beginNew("i32").append(i0).append(" == 0 ? 1 : 0").end()
            }
            is ShiftInstr -> {
                val i0 = pop(i.type)
                val i1 = pop(i.type)
                beginNew(i.type)
                writer.append(
                    if (i.isRight && i.isU) {
                        if (i.type == "i32") "(u32) " else "(u64) "
                    } else ""
                )
                writer.append(i1).append(
                    if (i.isRight) " >> "
                    else " << "
                ).append(i0).end()
            }
            Return -> {
                val offset = stack.size - function.results.size
                assertTrue(offset >= 0)
                begin().append("return")
                when (stack.size) {
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
                if (function.results.isEmpty()) {
                    begin().append("return /* unreachable */").end()
                } else {
                    begin().append("return { /* unreachable */ }").end()
                }
            }
            is Const -> {
                when (i.type) {
                    "f32" -> push(i.type, makeFloat(i.value) + "f")
                    "f64" -> push(i.type, makeFloat(i.value))
                    "i32" -> {
                        val v = if (i.value == "-2147483648") "(i32)(1u << 31)"
                        else i.value
                        push(i.type, v)
                    }
                    "i64" -> {
                        val v = if (i.value == "-9223372036854775808") "(i64)(1llu << 63)"
                        else i.value + "ll"
                        push(i.type, v)
                    }
                    else -> push(i.type, i.value)
                }
            }
            is UnaryInstruction -> {
                val i0 = pop(i.type)
                beginNew(i.type).append(i.call).append('(').append(i0).append(')').end()
            }
            is UnaryInstruction2 -> {
                val i0 = pop(i.popType)
                beginNew(i.pushType).append(i.prefix).append(i0).append(i.suffix).end()
            }
            is BinaryInstruction -> {
                val i0 = pop(i.type)
                val i1 = pop(i.type)
                if (i.operator.endsWith("(")) {
                    if (i.operator.startsWith("std::rot")) {
                        beginNew(i.type).append(i.operator).append( // cast to unsigned required
                            if (i0 == "i32") "(u32) " else "(u64) "
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
                beginNew("i32")
                if (i.castType != null) writer.append("(").append(i.castType).append(") ")
                writer.append(i1).append(' ').append(i.operator).append(' ')
                if (i.castType != null) writer.append("(").append(i.castType).append(") ")
                writer.append(i0).append(" ? 1 : 0").end()
            }
            is IfBranch -> {

                // get running parameters...
                val condition = pop("i32")
                val baseSize = stack.size - i.params.size
                for (j in i.params) {
                    beginNew(j).append(pop(j)).end()
                }

                if (i.ifFalse.isEmpty()) {
                    assertEquals(i.params.size, i.results.size)
                }

                val resultVars = i.results.map { gen() }
                for (ri in resultVars.indices) {
                    begin().append(i.results[ri]).append(' ')
                        .append(resultVars[ri]).append(" = 0").end()
                }

                begin().append("if (").append(condition).append(") {\n")
                val stackSave = ArrayList(stack)
                stack.subList(0, baseSize).clear()
                val stackForReset = ArrayList(stack)

                fun packResultsIntoOurStack(instructions: List<Instruction>) {
                    val lastInstr = instructions.lastOrNull()
                    if (lastInstr == Return || lastInstr == Unreachable || lastInstr is Jump) {
                        return
                    }
                    // pack results into our stack somehow...
                    // resultVars[i] = stack[i].name
                    for (j in i.results.indices.reversed()) {
                        val srcVar = pop(i.results[j])
                        val dstVar = resultVars[j]
                        begin().append(dstVar).append(" = ").append(srcVar).end()
                    }
                }

                fun writeBranchContents(instructions: List<Instruction>) {
                    depth++
                    for (instr in instructions) {
                        writeInstruction(instr)
                    }
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
                val type = parser.types[i.type]!!
                val tmpType = gen()
                val tmpVar = gen()
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
            is LoopInstr -> {
                val resultNames = i.results.map { gen() }
                for (j in i.results.indices) {
                    begin().append(i.results[j]).append(' ')
                        .append(resultNames[j]).append(" = 0;\n")
                }
                // to do check if the label is used
                begin().append(i.label).append(": while (true) {\n")
                val stackSave = ArrayList(stack)
                stack.clear()
                depth++
                val lastIsContinue = (i.body.lastOrNull() as? Jump)?.label == i.label
                for (ii in 0 until i.body.size - lastIsContinue.toInt()) {
                    writeInstruction(i.body[ii])
                }
                if (!lastIsContinue) {
                    // save results
                    for (j in i.results.indices.reversed()) {
                        begin().append(resultNames[j]).append(" = ")
                            .append(pop(i.results[j])).end()
                    }
                    begin().append("break;\n")
                } else assertTrue(i.results.isEmpty())
                depth--
                stack.clear()
                stack.addAll(stackSave)
                for (j in i.results.indices) {
                    push(i.results[j], resultNames[j])
                }
                begin().append("}\n")
            }
            is Jump -> {
                begin().append("goto ").append(i.label).append(";\n")
            }
            is JumpIf -> {
                val condition = pop("i32")
                begin().append("if (").append(condition).append(" != 0) { goto ")
                    .append(i.label).append("; }\n")
            }
            is SwitchCase -> {
                // big monster, only 1 per function allowed, afaik
                // assertEquals(1, depth)
                assertEquals(0, stack.size)
                depth++
                assertEquals(0, i.cases[0].size)
                for (j in 1 until i.cases.size) {
                    stack.clear()
                    // assertEquals(0, stack.size)
                    depth--
                    begin().append("case").append(j).append(": {\n")
                    depth++
                    val instructions = i.cases[j]
                    assertTrue(instructions.size >= 2)
                    val realLast = instructions.last()
                    assertTrue(realLast is Jump) // for while(true)-branch
                    var skipped = 2
                    while (true) {
                        val tmp = instructions[instructions.size - skipped]
                        if (tmp is LocalSet && tmp.name.startsWith("s") &&
                            (tmp.name.endsWith("32") || tmp.name.endsWith("64"))
                        ) {
                            // println("skipping ${tmp.name}")
                            skipped++
                        } else break
                    }
                    val last = instructions[instructions.size - skipped]
                    if (last != Unreachable) {
                        assertTrue(last is LocalSet && last.name == "lbl")
                        val preLast = instructions[instructions.size - (skipped + 1)]
                        assertTrue(preLast is IfBranch || (preLast is Const && preLast.type == "i32"))
                        // find end:
                        //   - i32.const 2 local.set $lbl
                        //   - (if (result i32) (then i32.const 4) (else i32.const 7)) local.set $lbl
                        for (k in 0 until instructions.size - (skipped + 1)) {
                            writeInstruction(instructions[k])
                        }
                        fun executeStackSaving() {
                            // println("executing stack saving, skipped: $skipped, length: ${instructions.size}")
                            for (k in instructions.size - (skipped - 1) until instructions.size - 1) {
                                // println("  stack saving[$k]: ${instructions[k]}")
                                writeInstruction(instructions[k])
                            }
                        }
                        if (preLast is IfBranch) {
                            // save branch
                            val branch = pop("i32")
                            executeStackSaving()
                            assertEquals(1, preLast.ifTrue.size)
                            assertEquals(1, preLast.ifFalse.size)
                            begin().append("if (").append(branch).append(") {\n")
                            depth++
                            begin().append("goto case").append((preLast.ifTrue[0] as Const).value).end()
                            depth--
                            begin().append("} else {\n")
                            depth++
                            begin().append("goto case").append((preLast.ifFalse[0] as Const).value).end()
                            depth--
                            begin().append("}\n")
                        } else {
                            executeStackSaving()
                            preLast as Const
                            begin().append("goto case").append(preLast.value).end()
                        }
                    } else {
                        for (k in 0 until instructions.size - skipped) {
                            writeInstruction(instructions[k])
                        }
                    }
                    depth--
                    begin().append("}\n")
                    depth++
                }
                depth--
                begin().append("case0:\n") // exit switch-case
            }
            Drop -> stack.pop()
            else -> throw NotImplementedError(i.toString())
        }
    }
    for (local in function.locals) {
        begin().append(local.type).append(' ').append(local.name).append(" = 0").end()
    }
    for (instr in function.body) {
        writeInstruction(instr)
    }
    when (function.body.lastOrNull()) {
        Return, Unreachable -> {}
        else -> writeInstruction(Return)
    }
    writer.append("}\n")
}
