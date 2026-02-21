package utils

import gIndex
import globals
import interpreter.WASMEngine
import interpreter.functions.TrackCallocInstr
import interpreter.memory.MemoryOptimizer
import interpreter.memory.StaticInitRemover
import me.anno.utils.Clock
import me.anno.utils.assertions.assertTrue
import me.anno.utils.files.Files.formatFileSize
import me.anno.utils.types.Floats.f1
import org.apache.logging.log4j.LogManager
import translator.GeneratorIndex.nthGetterMethods
import translator.GeneratorIndex.translatedMethods
import utils.StaticClassIndices.FIRST_ARRAY
import utils.StaticClassIndices.LAST_ARRAY
import kotlin.math.ceil
import kotlin.math.min

object CallStaticInit {

    private val LOGGER = LogManager.getLogger(CallStaticInit::class)

    private var time0 = 0L
    private var instr0 = 0L
    private var memory0 = 0

    private val debugInfo = StringBuilder2()

    private fun initStats(engine: WASMEngine, time0i: Long) {
        time0 = time0i
        instr0 = engine.instructionCounter
        memory0 = engine.globals["allocationPointer"]!!.toInt()
    }

    private fun printStats(engine: WASMEngine, name: String, i: Int) {

        debugInfo.append("[$i] $name\n")

        if (WASMEngine.printCallocSummary) {
            val minPrintedCount = 0
            val maxPrintedClasses = 10

            val allocations = TrackCallocInstr.counters.entries
                .filter { it.value >= minPrintedCount }
                .sortedByDescending { it.value }
            if (allocations.isNotEmpty()) {
                debugInfo.append("   Allocations:\n")
                for (k in 0 until min(allocations.size, maxPrintedClasses)) {
                    val (classId, count) = allocations[k]
                    val className = gIndex.classNames[classId]
                    if (classId in FIRST_ARRAY..LAST_ARRAY) {
                        debugInfo.append("   - ${count}x $className [${TrackCallocInstr.arraySize[classId]}]\n")
                    } else {
                        val instanceSize = gIndex.getInstanceSize(className)
                        debugInfo.append("   - ${count}x $className ($instanceSize)\n")
                    }
                }
                if (allocations.size > maxPrintedClasses) {
                    val more = allocations.size - maxPrintedClasses
                    val total = allocations.sumOf { it.value }
                    debugInfo.append("     ... ($more more, $total total)\n")
                }
            }
            TrackCallocInstr.arraySize.fill(0)
            TrackCallocInstr.counters.clear()
        }

        val minLogInstructions = 1_000_000L
        val minLogSize = 64_000

        val timeI = System.nanoTime()
        val instrI = engine.instructionCounter
        val memory1 = engine.globals["allocationPointer"]!!.toInt()
        if (instrI - instr0 > minLogInstructions)
            debugInfo.append(
                "   " +
                        "${((instrI - instr0) / 1e6f).f1()} MInstr, " +
                        "${ceil((timeI - time0) / 1e6f).toInt()} ms, " +
                        "${((instrI - instr0) * 1e3f / (timeI - time0)).toInt()} MInstr/s\n"
            )

        if (memory1 - memory0 > minLogSize) {
            debugInfo.append("   +").append((memory1 - memory0).formatFileSize()).append('\n')
        }

        memory0 = memory1
        instr0 = instrI
        time0 = timeI
    }

    private fun callMethod(engine: WASMEngine, name: String, i: Int) {
        engine.executeFunction(name)
        if (printDebug) printStats(engine, name, i)
    }

    private fun createEngine(originalMemory: Int): WASMEngine {
        val extraMemory = 6 shl 20
        val engine = WASMEngine(originalMemory + extraMemory)
        assertTrue(globals.isNotEmpty()) // should not be empty
        engine.registerGlobals(globals)
        engine.registerSpecialFunctions()
        engine.registerMemorySections(dataSections)
        assertTrue(translatedMethods.isNotEmpty()) // should not be empty
        engine.registerFunctions(translatedMethods.values)
        engine.registerFunctions(helperMethods.values)
        engine.registerFunctions(nthGetterMethods.values)
        engine.resolveCalls()
        assertTrue(functionTable.isNotEmpty()) // usually should not be empty
        engine.registerFunctionTable(functionTable)
        return engine
    }

    fun callStaticInitAtCompileTime(
        ptr: Int, staticInitFunctions: List<MethodSig>,
        printer: StringBuilder2
    ): Int {

        val clock = Clock(LOGGER)

        // create VM
        val engine = createEngine(ptr)

        // call all static init functions;
        // partially sort these methods by dependencies
        //  for better code-complexity and allocation measurements
        val time0i = System.nanoTime()

        LOGGER.info("Total static init functions: ${staticInitFunctions.size}")
        initStats(engine, time0i)
        callMethod(engine, "init", -1)
        for (i in staticInitFunctions.indices) {
            val sig = staticInitFunctions[i]
            callMethod(engine, methodName(sig), i)
        }

        if (printDebug) {
            debugFolder.getChild("callStaticInit.txt")
                .writeBytes(debugInfo.values, 0, debugInfo.size)
        }

        // calculate how much new memory was used
        val timeI = System.nanoTime()
        val allocationStart = engine.globals["allocationStart"]!!.toLong()
        val allocationPointer = engine.globals["allocationPointer"]!!.toLong()
        LOGGER.info("Base Memory: ($allocationStart) ${allocationStart.formatFileSize()}")
        LOGGER.info("Allocated ${(allocationPointer - allocationStart).formatFileSize()} during StaticInit")
        LOGGER.info("Executed ${engine.instructionCounter} instructions for StaticInit")
        clock.stop("calls, ${(engine.instructionCounter * 1e3f / (timeI - time0i)).f1()} MInstr/s")

        val ptr1: Int
        if (true) {
            // todo clear gaps field, because it no longer is valid, when we optimize the memory
            ptr1 = MemoryOptimizer.optimizeMemory(engine, printer)
            clock.stop("optimizeMemory")
        } else {
            ptr1 = MemoryOptimizer.justAppendData(engine, printer)
            clock.stop("justAppendData")
        }

        StaticInitRemover.removeStaticInit()
        LOGGER.info("New Base Memory: $allocationStart (${allocationStart.formatFileSize()})")
        clock.stop("removeStaticInit")

        clock.total("StaticInit WASM-VM")
        return ptr1
    }
}