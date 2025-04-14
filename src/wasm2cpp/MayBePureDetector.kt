package wasm2cpp

import highlevel.*
import optimizer.InstructionProcessor
import utils.MethodSig
import utils.methodName
import wasm.instr.*
import wasm.parser.FunctionImpl
import wasm.parser.Import

class MayBePureDetector(
    private val functionByName: Map<String, FunctionImpl>,
    var dst: HashSet<String>
) : InstructionProcessor {

    var canBePure = true
    override fun processInstruction(instr: Instruction) {
        when (instr) {
            is Call -> processCall(instr.name)
            is GlobalSet,
            is StoreInstr -> canBePure = false
            is CallIndirect -> {
                // call-indirect could call only a specific function maybe, but that's too complicated to find out ;)
                processIndirectCall(instr.options)
            }
            is LocalSet, is ParamSet, is LocalGet, is ParamGet, is GlobalGet,
            is SimpleInstr, is Comment, is FieldGetInstr, is Const, is StringConst, is Jumping,
            is PtrDupInstr -> {
                // instruction is pure
            }
            is IfBranch, is LoopInstr -> {
                // content is handled by InstructionProcessor
            }
            is FieldSetInstr -> canBePure = false
            is ResolvedMethodInstr -> processCall(instr.callName)
            is UnresolvedMethodInstr -> processIndirectCall(instr.resolvedMethods)
            else -> throw NotImplementedError("Unknown instruction ${instr.javaClass}")
        }
    }

    private fun processCall(callName: String) {
        val isImport = functionByName[callName] is Import
        if (isImport) canBePure = false
        else dst.add(callName)
    }

    private fun processIndirectCall(options: Set<MethodSig>?) {
        if (options == null) canBePure = false
        else dst.addAll(options.map { methodName(it) })
    }
}