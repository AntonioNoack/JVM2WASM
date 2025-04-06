package wasm2cpp

import globals
import highlevel.HighLevelInstruction
import interpreter.WASMEngine
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertTrue
import org.apache.logging.log4j.LogManager
import translator.GeneratorIndex
import translator.JavaTypes.convertTypeToWASM
import utils.WASMType
import utils.functionTable
import wasm.instr.BreakableInstruction
import wasm.instr.IfBranch
import wasm.instr.Instruction
import wasm.instr.LoopInstr
import wasm.parser.FunctionImpl
import wasm.parser.GlobalVariable
import wasm.parser.Module

object ParserValidation {

    private val LOGGER = LogManager.getLogger(ParserValidation::class)

    fun validate(parser: Module) {
        validateParsedFunctionsWithOriginals(parser.functions)
        validateFunctionTable(parser.functionTable)
        validateGlobals(parser.globals)
    }

    private fun validateGlobals(parsed1: Map<String, GlobalVariable>) {
        if (globals.isEmpty()) return
        var passed = 0
        for ((name, parsed) in parsed1) {
            val original = globals[name] ?: continue
            assertEquals(original.name, parsed.name)
            assertEquals(original.wasmType, parsed.wasmType)
            assertEquals(original.initialValue, parsed.initialValue)
            assertEquals(original.isMutable, parsed.isMutable)
            passed++
        }
        LOGGER.info("Validated $passed/${parsed1.size} globals")
    }

    private fun validateFunctionTable(parsed: List<String>) {
        if (functionTable.isEmpty()) return
        assertEquals(functionTable, parsed)
        LOGGER.info("Validated function table, ${parsed.size} entries")
    }

    private fun validateParsedFunctionsWithOriginals(parsed1: Collection<FunctionImpl>) {
        val originalByName = GeneratorIndex.translatedMethods
            .values.associateBy { it.funcName }
        var passed = 0
        for (parsed in parsed1) {
            val original = originalByName[parsed.funcName] ?: continue
            validateEqual(original, parsed)
            passed++
        }
        LOGGER.info("Validated $passed/${parsed1.size} functions")
    }

    private fun validateEqual(original: FunctionImpl, parsed: FunctionImpl) {
        assertEquals(original.funcName, parsed.funcName)
        assertEquals(original.params.map { it.wasmType }, parsed.params.map { it.wasmType })
        assertEquals(original.results.map { convertTypeToWASM(it).wasmName }, parsed.results)
        assertEquals(original.locals.map { it.wasmType }, parsed.locals.map { it.wasmType })
        validateEqual(original.body, parsed.body, original.funcName)
    }

    private val tmp1 = ArrayList<Instruction>()
    private val tmp2 = ArrayList<Instruction>()
    private fun validateEqual(original: List<Instruction>, parsed: List<Instruction>, funcName: String) {
        serialize(original, tmp1)
        assertTrue(tmp1.none { it is HighLevelInstruction })
        serialize(parsed, tmp2)
        assertTrue(tmp2.none { it is HighLevelInstruction })
        assertTrue(tmp1 == tmp2) {
            printBodies(original, parsed)
            "Expected equal bodies in '$funcName'"
        }
        tmp1.clear()
        tmp2.clear()
    }

    data class PseudoInstr(val name: String) : Instruction {
        override fun execute(engine: WASMEngine): String? {
            throw NotImplementedError()
        }
    }

    data class PseudoInstr2(val label: String, val params: List<WASMType>, val results: List<WASMType>) : Instruction {
        constructor(instr: BreakableInstruction) : this(
            instr.label, instr.params.map { convertTypeToWASM(it) },
            instr.results.map { convertTypeToWASM(it) })

        override fun execute(engine: WASMEngine): String? {
            throw NotImplementedError()
        }
    }

    private val IfPseudo = PseudoInstr("if")
    private val ElsePseudo = PseudoInstr("else")
    private val EndPseudo = PseudoInstr("end")
    private val LoopPseudo = PseudoInstr("loop")

    private fun serialize(instructions: List<Instruction>, dst: ArrayList<Instruction>) {
        for (i in instructions.indices) {
            when (val instr = instructions[i]) {
                is HighLevelInstruction -> {
                    serialize(instr.toLowLevel(), dst)
                }
                is IfBranch -> {
                    dst.add(IfPseudo)
                    serialize(instr.ifTrue, dst)
                    dst.add(ElsePseudo)
                    serialize(instr.ifFalse, dst)
                    dst.add(EndPseudo)
                }
                is LoopInstr -> {
                    dst.add(LoopPseudo)
                    dst.add(PseudoInstr2(instr))
                    serialize(instr.body, dst)
                    dst.add(EndPseudo)
                }
                is BreakableInstruction -> throw NotImplementedError()
                else -> dst.add(instr)
            }
        }
    }

    private fun printBodies(original: List<Instruction>, parsed: List<Instruction>) {
        val diff = original.indices
            .filter { original[it] != parsed[it] }
        println("diff: $diff")
        for (i in diff) {
            println("[$i] ${original[i]} != ${parsed[i]}")
        }
    }
}