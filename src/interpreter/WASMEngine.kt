package interpreter

import interpreter.functions.*
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFail
import me.anno.utils.assertions.assertSame
import me.anno.utils.assertions.assertTrue
import org.apache.logging.log4j.LogManager
import utils.Param.Companion.toParams
import utils.WASMTypes.*
import utils.is32Bits
import utils.ptrType
import wasm.instr.*
import wasm.instr.Instructions.Return
import wasm.instr.Instructions.Unreachable
import wasm.parser.DataSection
import wasm.parser.FunctionImpl
import wasm.parser.GlobalVariable
import java.nio.ByteBuffer
import java.nio.ByteOrder

// a WASM runtime, so we can pre-initialize code, e.g. run all static_init-s at compile-time
class WASMEngine(memorySize: Int) {

    companion object {
        private val LOGGER = LogManager.getLogger(WASMEngine::class)
        const val RETURN_LABEL = "return"
    }

    fun registerMemorySections(sections: List<DataSection>) {
        for (section in sections) {
            registerMemorySection(section)
        }
    }

    private fun registerMemorySection(section: DataSection) {
        val data = buffer
        data.position(section.startIndex)
        data.put(section.content)
    }

    private fun replaceFunction(
        instructions: List<Instruction>,
        byLabel: HashMap<String, BreakableInstruction>
    ): List<Instruction> {
        return instructions.map { replaceFunction(it, byLabel) }
    }

    private fun replaceFunction(
        i: Instruction,
        byLabel: HashMap<String, BreakableInstruction>
    ): Instruction {
        return when (i) {
            is Call -> {
                // todo singleInstr are loading to corruption results... what??
                /*val singleInstr = singleInstrFunctions[i.name]
                singleInstr ?: */ResolvedCall(getFunction(i.name))
            }
            is LoopInstr -> {
                val newInstr = LoopInstr(i.label, i.body, i.params, i.results)
                byLabel[i.label] = newInstr
                newInstr.body = replaceFunction(i.body, byLabel)
                newInstr
            }
            is SwitchCase -> {
                val newInstr = SwitchCase(i.label, i.cases, i.params, i.results)
                byLabel[i.label] = newInstr
                newInstr.cases = i.cases.map { caseI -> replaceFunction(caseI, byLabel) }
                newInstr
            }
            is IfBranch -> IfBranch(
                replaceFunction(i.ifTrue, byLabel),
                replaceFunction(i.ifFalse, byLabel),
                i.params, i.results
            )
            else -> i
        }
    }

    fun getFunction(name: String): FunctionImpl {
        return functionByName[name]
            ?: customFunctions.getOrPut(name) { createMissingFunction(name) }
    }

    fun registerGlobals(globalVariables: List<GlobalVariable>) {
        val prefix = "global_"
        for (global in globalVariables) {
            val name = global.name
            assertTrue(name.startsWith(prefix))
            globals[name.substring(prefix.length)] = global.initialValue
        }
    }

    fun str(addr: Int): String? {
        if (addr == 0) return null
        val data = buffer
        // todo if alignment is required, address will be at 8
        data.position(addr + 4)
        val dataAddr = if (is32Bits) data.getInt() else data.getLong().toInt()
        assertTrue(dataAddr != 0)
        data.position(dataAddr + 4)
        val length = data.getInt()
        val chars = ByteArray(length)
        data.get(chars)
        return String(chars)
    }

    fun registerSpecialFunctions() {
        registerEmptyFunction("lockMallocMutex")
        registerEmptyFunction("unlockMallocMutex")
        registerFunction(
            "jvm_JVM32_printStackTraceLine_ILjava_lang_StringLjava_lang_StringIV",
            listOf(i32, ptrType, ptrType, i32), emptyList(), PrintStackTraceLine
        )
        registerFunction(
            "jvm_JavaLang_printString_Ljava_lang_StringZV",
            listOf(ptrType, i32), emptyList(), PrintString
        )
        registerFunction("jvm_JVM32_trackCalloc_IV", listOf(i32), emptyList(), listOf(Return))
        registerFunction("fcmpg", listOf(f32, f32), listOf(i32), FloatCompare(+1))
        registerFunction("fcmpl", listOf(f32, f32), listOf(i32), FloatCompare(-1))
        registerFunction("dcmpg", listOf(f64, f64), listOf(i32), FloatCompare(+1))
        registerFunction("dcmpl", listOf(f64, f64), listOf(i32), FloatCompare(-1))
        registerFunction("jvm_JavaLang_fillD2S_ACDI", listOf(ptrType, f64), listOf(i32), FillDoubleToString)
        registerFunction("jvm_JavaX_fillDate_ACI", listOf(ptrType), listOf(i32), FillDateToString)
        registerGetter("jvm_JVM32_getAllocatedSize_I", i32) { it.bytes.size }
        registerGetter("java_lang_System_nanoTime_J", i64) { System.nanoTime() }
        registerGetter("java_lang_System_currentTimeMillis_J", i64) { System.currentTimeMillis() }
        registerLogFunction("d")
        registerLogFunction("?ii")
    }

    private fun registerLogFunction(code: String) {
        val signature = code.toList()
            .joinToString("") { codeI ->
                when (codeI) {
                    '?' -> "Ljava_lang_String"
                    'i' -> "I"
                    'l' -> "J"
                    'f' -> "F"
                    'd' -> "D"
                    else -> assertFail()
                }
            }
        registerFunction(
            "jvm_NativeLog_log_${signature}V",
            code.map { codeI ->
                when (codeI) {
                    '?' -> ptrType
                    'i' -> i32
                    'l' -> i64
                    'f' -> f32
                    'd' -> f64
                    else -> assertFail()
                }
            },
            emptyList(),
            NativeLogInstr(code)
        )
    }

    private fun registerFunction(
        name: String, params: List<String>, results: List<String>,
        body: List<Instruction>
    ) {
        customFunctions[name] = FunctionImpl(
            name, params.toParams(),
            results, emptyList(), body, false
        )
    }

    private val singleInstrFunctions = HashMap<String, Instruction>()
    private fun registerFunction(name: String, params: List<String>, results: List<String>, code: Instruction) {
        singleInstrFunctions[name] = code
        val instructions = ArrayList<Instruction>(2 + params.size)
        for (i in params.indices) {
            instructions.add(ParamGet[i])
        }
        instructions.add(code)
        instructions.add(Return)
        registerFunction(name, params, results, instructions)
    }

    private fun registerGetter(name: String, result: String, getter: (WASMEngine) -> Number) {
        registerFunction(name, emptyList(), listOf(result), GetterInstr(getter))
    }

    private fun registerEmptyFunction(name: String) {
        registerFunction(name, emptyList(), emptyList(), listOf(Return))
    }

    fun registerFunctions(functions: Collection<FunctionImpl>) {
        // prepare functions for linking
        functionByName = functions.associate { it.funcName to it.withBody(it.body) }
        for (func in functionByName.values) {
            func.body = replaceFunction(func.body, HashMap())
        }
    }

    fun registerFunctionTable(functions: List<String>) {
        functionTable = functions.map(::getFunction)
    }

    private fun createMissingFunction(name: String): FunctionImpl {
        val body = listOf(LogInstruction(LOGGER, "Missing $name"), Unreachable)
        // is empty params and result ok??? should be, because it never finishes
        return FunctionImpl(name, emptyList(), emptyList(), emptyList(), body, false)
    }

    lateinit var functionByName: Map<String, FunctionImpl>

    val globals = HashMap<String, Number>()

    val bytes = ByteArray(memorySize)
    val buffer: ByteBuffer = ByteBuffer.wrap(bytes)
        .order(ByteOrder.LITTLE_ENDIAN)

    val stack = ArrayList<Number>()
    val stackFrames = ArrayList<StackFrame>()

    lateinit var functionTable: List<FunctionImpl>

    private val customFunctions = HashMap<String, FunctionImpl>()

    fun push(value: Number) {
        stack.add(value)
    }

    fun pop(): Number {
        return stack.removeLast()
    }

    fun getParamIndex(index: Int): Int {
        val offset = stackFrames.last().stackStart
        return offset + index
    }

    fun getParam(index: Int): Number {
        return stack[getParamIndex(index)]
    }

    fun executeInstructions(instructions: List<Instruction>): String? {
        for (i in instructions.indices) {
            val instr = instructions[i]
            // println("Executing $i: $instr")
            val breakLabel = instr.execute(this)
            if (breakLabel != null) return breakLabel
        }
        return null
    }

    fun executeFunction(function: FunctionImpl) {
        val startStackSize = stack.size - function.params.size
        val stackFrame = StackFrame()
        stackFrame.stackStart = startStackSize
        stackFrames.add(stackFrame)
        val returnLabel = executeInstructions(function.body)
        assertEquals(RETURN_LABEL, returnLabel) {
            "Got $returnLabel as answer from ${function.funcName}"
        }
        stackFrame.locals.clear()
        assertSame(stackFrame, stackFrames.removeLast())

        // assert correct stack size
        assertTrue(startStackSize + function.params.size + function.results.size <= stack.size)
        // drop params from in-between stack
        stack.subList(startStackSize, stack.size - function.results.size).clear()
        assertEquals(startStackSize + function.results.size, stack.size)
    }

    // todo just put call parameters and return values onto the stack
    // todo for breaks, jump back somehow... using Throwables??? using

}