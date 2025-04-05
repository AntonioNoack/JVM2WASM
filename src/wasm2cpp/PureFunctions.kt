package wasm2cpp

import dependency.StaticDependencies.transpose
import highlevel.FieldGetInstr
import highlevel.FieldSetInstr
import highlevel.PtrDupInstr
import optimizer.InstructionProcessor
import org.apache.logging.log4j.LogManager
import utils.methodName
import wasm.instr.*
import wasm.parser.FunctionImpl
import wasm.parser.Import

/**
 * Find functions, which can be called out of order, because they are just reading memory or globals
 * */
class PureFunctions(
    private val imports: List<FunctionImpl>,
    private val functions: List<FunctionImpl>,
    private val functionByName: Map<String, FunctionImpl>
) {

    companion object {
        private val LOGGER = LogManager.getLogger(PureFunctions::class)
    }

    fun findPureFunctions(): Set<String> {

        val impureFunctions = ArrayList<String>(functions.size)
        impureFunctions.addAll(imports.map { it.funcName })

        val pureFunctions = HashSet<String>()
        val dependencies = HashMap<String, Set<String>>(functions.size)
        for (i in functions.indices) {
            val func = functions[i]
            val funcDependencies = HashSet<String>()
            val mayBePure = mayBePure(func, funcDependencies)
            val name = func.funcName
            if (mayBePure) pureFunctions.add(name)
            else impureFunctions.add(name)
            dependencies[name] = funcDependencies
        }

        // recursively remove calls to non-pure functions
        val calledBy = dependencies.transpose(::HashSet)
        while (true) {
            val impure = impureFunctions.removeLastOrNull() ?: break
            val called = calledBy[impure] ?: continue
            for (calledI in called) {
                if (pureFunctions.remove(calledI)) {
                    // new impure function was found
                    impureFunctions.add(calledI)
                }
            }
        }

        LOGGER.info("Pure functions: ${pureFunctions.size}/(${functions.size}+${imports.size})")
        return pureFunctions

    }

    private fun mayBePure(function: FunctionImpl, dst: HashSet<String>): Boolean {
        var canBePure = true
        InstructionProcessor { instruction ->
            when (instruction) {
                is Call -> {
                    val isImport = functionByName[instruction.name] is Import
                    if (isImport) canBePure = false
                    else dst.add(instruction.name)
                }
                is GlobalSet,
                is StoreInstr -> canBePure = false
                is CallIndirect -> {
                    // call-indirect could call only a specific function maybe, but that's too complicated to find out ;)
                    val options = instruction.options
                    if (options == null) canBePure = false
                    else dst.addAll(options.map { methodName(it) })
                }
                is LocalSet, is ParamSet, is LocalGet, is ParamGet, is GlobalGet,
                is SimpleInstr, is Comment, is FieldGetInstr, is Const, is Jumping,
                is PtrDupInstr -> {
                    // instruction is pure
                }
                is IfBranch, is LoopInstr -> {
                    // content is handled by InstructionProcessor
                }
                is FieldSetInstr -> canBePure = false
                else -> throw NotImplementedError("Unknown instruction $instruction")
            }
        }.process(function)
        return canBePure
    }
}