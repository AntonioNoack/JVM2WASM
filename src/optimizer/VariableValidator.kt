package optimizer

import jvm.custom.ThreadLocal2
import wasm.instr.*
import wasm.parser.FunctionImpl
import wasm.parser.LocalVariable

/**
 * find which variables are only set, which are only gotten,
 * which aren't used at all, and replace them with a drop/i32Const0
 * */
class VariableValidator {

    companion object {
        private val instances = ThreadLocal2 { VariableValidator() }
        val INSTANCE: VariableValidator get() = instances.get()
    }

    private val read = HashSet<String>(32)
    private val written = HashSet<String>(32)
    private val zeros = HashMap<String, Const>(32)
    val both = read

    private val variableFinder = InstructionProcessor { instr ->
        when (instr) {
            is LocalGet -> read.add(instr.name)
            is LocalSet -> written.add(instr.name)
        }
    }

    private val variableReplacer = InstructionReplacer { instructions ->
        for (i in instructions.indices) {
            when (val instr = instructions[i]) {
                is LocalGet -> {
                    if (instr.name !in both) {
                        instructions[i] = zeros[instr.name]!!
                    }
                }
                is LocalSet -> {
                    if (instr.name !in both) {
                        instructions[i] = Drop
                    }
                }
            }
        }
    }

    fun validate(function: FunctionImpl) {
        validate(function.body, function.locals)
    }

    fun validate(function: ArrayList<Instruction>, locals: List<LocalVariable>) {
        clear()
        variableFinder.process(function)

        for (i in locals.indices) {
            val local = locals[i]
            zeros[local.name] = Const.zero[local.type]!!
        }

        read.retainAll(written)

        if (locals.size != read.size) {
            variableReplacer.process(function)
        }
    }

    private fun clear() {
        written.clear()
        read.clear()
        zeros.clear()
    }

}