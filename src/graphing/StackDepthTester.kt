package graphing

import graphing.StackCallUtils.findCallByName
import graphing.StackCallUtils.getCallParams
import graphing.StackCallUtils.getCallResults
import graphing.StackCallUtils.hasSelfParam
import highlevel.HighLevelInstruction
import me.anno.utils.assertions.assertTrue
import me.anno.utils.types.Booleans.toInt
import utils.MethodSig
import wasm.instr.*
import wasm.instr.Instructions.Return
import wasm.instr.Instructions.Unreachable
import kotlin.math.min

object StackDepthTester {

    fun findMinDepth(sig: MethodSig, instructions: List<Instruction>, start: Int): Int {
        var depth = start
        var minDepth = start
        fun process(instr: Instruction) {
            when (instr) {
                is LocalGet, is ParamGet, is GlobalGet -> depth++
                is LocalSet, is ParamSet, is GlobalSet, Drop -> {
                    depth--
                    minDepth = min(minDepth, depth)
                }
                is BinaryInstruction -> {
                    depth -= 2
                    minDepth = min(minDepth, depth)
                    depth++
                }
                is UnaryInstruction -> {
                    minDepth = min(minDepth, depth - 1)
                }
                is Call -> {
                    val func = findCallByName(instr.name) ?: throw IllegalStateException("Missing $instr")
                    val params = getCallParams(func).size + hasSelfParam(func).toInt()
                    val results = getCallResults(func).size
                    minDepth = min(minDepth, depth - params)
                    depth += results - params
                }
                is CallIndirect -> {
                    val params = instr.type.params.size + 1  // +1 for index
                    val results = instr.type.results.size
                    minDepth = min(minDepth, depth - params)
                    depth += results - params
                }
                is IfBranch -> {
                    depth -= instr.params.size + 1 // +1 for condition
                    minDepth = min(minDepth, depth)
                    depth += instr.results.size
                }
                is LoopInstr -> {
                    depth -= instr.params.size
                    minDepth = min(minDepth, depth)
                    depth += instr.results.size
                }
                is Const, is StringConst -> depth++
                is HighLevelInstruction -> {
                    for (low in instr.toLowLevel()) {
                        process(low)
                    }
                }
                Return -> {
                    val params = getCallResults(sig).size
                    depth -= params
                    minDepth = min(minDepth, depth)
                }
                is Comment, Unreachable, is Jump -> {}
                is Jumping -> {
                    depth--
                    minDepth = min(minDepth, depth)
                }
                // loadInstr is handled by unary
                is StoreInstr -> {
                    depth -= 2
                    minDepth = min(minDepth, depth)
                }
                else -> throw NotImplementedError("Unknown instr: ${instr.javaClass.simpleName}")
            }
        }
        for (i in instructions.indices) {
            process(instructions[i])
        }
        assertTrue(minDepth >= 0)
        return minDepth - start
    }

}