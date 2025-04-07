package wasm2cpp

import highlevel.FieldGetInstr
import highlevel.FieldSetInstr
import highlevel.PtrDupInstr
import optimizer.InstructionProcessor
import utils.methodName
import wasm.instr.*
import wasm.parser.FunctionImpl
import wasm.parser.Import

class MayBePureDetector(
    private val functionByName: Map<String, FunctionImpl>,
    var dst: HashSet<String>
) : InstructionProcessor {

    var canBePure = true
    override fun processInstruction(instruction: Instruction) {
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
    }
}