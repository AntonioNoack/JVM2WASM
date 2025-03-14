package interpreter

import gIndex
import interpreter.functions.*
import jvm.JVM32.objectOverhead
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFail
import me.anno.utils.assertions.assertSame
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.lists.Lists.pop
import org.apache.logging.log4j.LogManager
import utils.Param.Companion.toParams
import utils.StaticClassIndices
import utils.StaticFieldOffsets
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
// todo we can also use this to write unit tests for our static analysis :3
// todo when static-init-at-compile-time works, we can re-resolve all dependencies, and whether fields are written,
//  and a) remove lots of functions (10%?), b) inline lots of fields (>50% of all static ones?)
class WASMEngine(memorySize: Int) {

    companion object {
        private val LOGGER = LogManager.getLogger(WASMEngine::class)
        const val RETURN_LABEL = "return"
        var printStackPush = false
    }

    var instructionCounter = 0L

    val globals = HashMap<String, Number>()

    var bytes = ByteArray(memorySize)
    var buffer: ByteBuffer = ByteBuffer.wrap(bytes)
        .order(ByteOrder.LITTLE_ENDIAN)

    val stack = ArrayList<Number>()
    val stackFrames = ArrayList<StackFrame>()

    lateinit var functionTable: List<FunctionImpl>
    private val customFunctions = HashMap<String, FunctionImpl>()
    private var functionByName: HashMap<String, FunctionImpl> = HashMap()

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

    private fun resolveCalls(
        instructions: List<Instruction>,
        byLabel: HashMap<String, BreakableInstruction>
    ): List<Instruction> {
        return instructions.map { resolveCalls(it, byLabel) }
    }

    private fun resolveCalls(i: Instruction, byLabel: HashMap<String, BreakableInstruction>): Instruction {
        return when (i) {
            is Call -> {
                // todo singleInstr are loading to corruption results... what??
                /*val singleInstr = singleInstrFunctions[i.name]
                singleInstr ?: */ResolvedCall(getFunction(i.name))
            }
            is LoopInstr -> {
                val newInstr = LoopInstr(i.label, i.body, i.params, i.results)
                byLabel[i.label] = newInstr
                newInstr.body = resolveCalls(i.body, byLabel)
                newInstr
            }
            is SwitchCase -> {
                val newInstr = SwitchCase(i.label, i.cases, i.params, i.results)
                byLabel[i.label] = newInstr
                newInstr.cases = i.cases.map { caseI -> resolveCalls(caseI, byLabel) }
                newInstr
            }
            is IfBranch -> IfBranch(
                resolveCalls(i.ifTrue, byLabel),
                resolveCalls(i.ifFalse, byLabel),
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

    fun readString(addr: Number): String? {
        return readString(addr.toInt())
    }

    private fun readString(addr: Int): String? {
        if (addr == 0) return null
        val data = buffer
        data.position(addr)
        val stringClassId = data.getInt() and 0xffffff
        assertEquals(StaticClassIndices.STRING, stringClassId) {
            "Expected string class for $addr, got $stringClassId" +
                    " (${gIndex.classNamesByIndex.getOrNull(stringClassId)})"
        }
        data.position(addr + StaticFieldOffsets.OFFSET_STRING_VALUE)
        val dataAddr = if (is32Bits) data.getInt() else data.getLong().toInt()
        assertTrue(dataAddr != 0)
        if (dataAddr !in 0 until data.capacity()) {
            throw IllegalStateException("Segfault! $addr -> $dataAddr !in 0 until ${data.capacity()}")
        }
        data.position(dataAddr + objectOverhead)
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
            listOf(i32, ptrType, ptrType, i32), emptyList(), if (printStackPush) PrintStackTraceLine else Return
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
        registerFunction("jvm_JavaLang_printByte_IZV", listOf(i32, i32), emptyList(), PrintByteInstr)
        registerFunction("jvm_JavaLang_printFlush_ZV", listOf(i32), emptyList(), PrintFlushInstr)
        registerFunction("jvm_JVM32_grow_IZ", listOf(i32), listOf(i32), GrowMemoryInstr)
        registerFunction(
            "jvm_JavaThrowable_printStackTraceHead_Ljava_lang_StringLjava_lang_StringV",
            listOf(ptrType, ptrType), emptyList(), PrintStackTraceHead
        )
        registerFunction(
            "jvm_JavaThrowable_printStackTraceLine_Ljava_lang_StringLjava_lang_StringIV",
            listOf(ptrType, ptrType, i32), emptyList(), PrintStackTraceLine2
        )
        registerEmptyFunction("jvm_JavaThrowable_printStackTraceEnd_V")
        registerGetter("jvm_JVM32_getAllocatedSize_I", i32) { it.bytes.size }
        registerGetter("java_lang_System_nanoTime_J", i64) { System.nanoTime() }
        registerGetter("java_lang_System_currentTimeMillis_J", i64) { System.currentTimeMillis() }
        for (code in "i,?,??,?i,???,?i?,?ii,?iii".split(',')) {
            registerLogFunction(code)
        }
        registerUnaryMathFunction("java_lang_StrictMath_log_DD", StrictMath::log)
        registerUnaryMathFunction("java_lang_StrictMath_tan_DD", StrictMath::tan)
        registerUnaryMathFunction("java_lang_StrictMath_sin_DD", StrictMath::sin)
        registerUnaryMathFunction("java_lang_StrictMath_cos_DD", StrictMath::cos)
        registerUnaryMathFunction("java_lang_StrictMath_exp_DD", StrictMath::exp)
        registerBinaryMathFunction("java_lang_StrictMath_hypot_DDD", StrictMath::hypot)
        registerBinaryMathFunction("java_lang_StrictMath_atan2_DDD", StrictMath::atan2)
        registerBinaryMathFunction("java_lang_StrictMath_pow_DDD", StrictMath::pow)
        registerFunction("java_lang_Math_round_FI", listOf(f32), listOf(i32), UnaryMathFunction<Float>(Math::round))
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

    private fun registerUnaryMathFunction(name: String, impl: (Double) -> Double) {
        registerFunction(name, listOf(f64), listOf(f64), UnaryMathFunction(impl))
    }

    private fun registerBinaryMathFunction(name: String, impl: (Double, Double) -> Double) {
        registerFunction(name, listOf(f64, f64), listOf(f64), BinaryMathFunction(impl))
    }

    private fun registerFunction(name: String, params: List<String>, results: List<String>, code: Instruction) {
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
        if (functionByName.isEmpty()) functionByName = HashMap(functions.size)
        val byName = functionByName
        for (func in functions) {
            byName[func.funcName] = func.withBody(func.body)
        }
    }

    fun resolveCalls() {
        for (func in functionByName.values) {
            func.body = resolveCalls(func.body, HashMap())
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
            instructionCounter++
            val instr = instructions[i]
            val breakLabel = instr.execute(this)
            if (breakLabel != null) return breakLabel
        }
        return null
    }

    fun executeFunction(name: String): Boolean {
        val function = functionByName[name]
            ?: return false
        executeFunction(function)
        return true
    }

    fun executeFunction(function: FunctionImpl) {
        val startStackSize = stack.size - function.params.size
        val stackFrame = StackFrame.pool.create()
        stackFrame.stackStart = startStackSize
        stackFrames.add(stackFrame)
        val returnLabel = try {
            executeInstructions(function.body)
        } catch (e: IllegalStateException) {
            stackFrames.pop()
            println("${stackFrames.size}: ${function.funcName}")
            throw e
        }
        assertEquals(RETURN_LABEL, returnLabel) {
            "Got $returnLabel as answer from ${function.funcName}"
        }
        assertSame(stackFrame, stackFrames.removeLast())
        stackFrame.locals.clear()
        StackFrame.pool.sub(1)

        // assert correct stack size
        assertTrue(startStackSize + function.params.size + function.results.size <= stack.size)
        // drop params from in-between stack
        stack.subList(startStackSize, stack.size - function.results.size).clear()
        assertEquals(startStackSize + function.results.size, stack.size)
    }
}