package wasm2cpp

import globals
import highlevel.HighLevelInstruction
import interpreter.WASMEngine
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertTrue
import org.apache.logging.log4j.LogManager
import translator.GeneratorIndex
import utils.StringBuilder2
import utils.functionTable
import wasm.instr.*
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

    fun validateGlobals(parsed1: Map<String, GlobalVariable>) {
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

    fun validateFunctionTable(parsed: List<String>) {
        if (functionTable.isEmpty()) return
        assertEquals(functionTable, parsed)
        LOGGER.info("Validated function table, ${parsed.size} entries")
    }

    fun validateParsedFunctionsWithOriginals(parsed1: Collection<FunctionImpl>) {
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

    fun validateEqual(original: FunctionImpl, parsed: FunctionImpl) {
        assertEquals(original.funcName, parsed.funcName)
        assertEquals(original.params, parsed.params)
        assertEquals(original.results, parsed.results)
        assertEquals(original.locals, parsed.locals)
        validateEqual(original.body, parsed.body, original.funcName)
    }

    private val tmp1 = ArrayList<Instruction>()
    private val tmp2 = ArrayList<Instruction>()
    fun validateEqual(original: List<Instruction>, parsed: List<Instruction>, funcName: String) {
        serialize(original, tmp1)
        serialize(parsed, tmp2)
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

    data class PseudoInstr2(val label: String, val params: List<String>, val results: List<String>) : Instruction {
        constructor(instr: BreakableInstruction) : this(instr.label, instr.params, instr.results)

        override fun execute(engine: WASMEngine): String? {
            throw NotImplementedError()
        }
    }

    val IfPseudo = PseudoInstr("if")
    val ElsePseudo = PseudoInstr("else")
    val EndPseudo = PseudoInstr("end")
    val LoopPseudo = PseudoInstr("loop")
    val BlockPseudo = PseudoInstr("block")
    val SwitchPseudo = PseudoInstr("Switch")
    val CasePseudo = PseudoInstr("case")

    fun serialize(instructions: List<Instruction>, dst: ArrayList<Instruction>) {
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
                is BlockInstr -> {
                    dst.add(BlockPseudo)
                    dst.add(PseudoInstr2(instr))
                    serialize(instr.body, dst)
                    dst.add(EndPseudo)
                }
                is BreakableInstruction -> throw NotImplementedError()
                else -> dst.add(instr)
            }
        }
    }

    fun printBodies(original: List<Instruction>, parsed: List<Instruction>) {
        val diff = original.indices
            .filter { original[it] != parsed[it] }
        println("diff: $diff")
        for (i in diff) {
            println("[$i] ${original[i]} != ${parsed[i]}")
        }
    }

    fun printBody(label: String, instructions: List<Instruction>) {
        val builder = StringBuilder2()
        builder.append(label).append(":\n")
        for (instr in instructions) {
            instr.toString(1, builder)
            builder.append('\n')
        }
        println(builder)
    }

}