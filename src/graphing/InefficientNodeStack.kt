package graphing

import graphing.LoadStoreStack.loadStackPrepend
import graphing.LoadStoreStack.storeStackAppend
import graphing.StructuralAnalysis.Companion.renumber
import translator.MethodTranslator
import utils.Builder
import utils.WASMType
import wasm.instr.Const.Companion.i32Const
import wasm.instr.Const.Companion.i32Const0
import wasm.instr.IfBranch
import wasm.instr.Instruction
import wasm.instr.Instructions.I32EQ
import wasm.instr.Instructions.Unreachable
import wasm.instr.Jump
import wasm.instr.LoopInstr

/**
 * If StackAnalysis fails, use this work-around.
 * */
object InefficientNodeStack {
    fun createNodeStack(nodes: MutableList<GraphingNode>, mt: MethodTranslator) {
        val caseVariable = mt.variables.addPrefixedLocalVariable("stack", WASMType.I32, "int")

        // make node.index usable
        renumber(nodes)

        var lastCase: ArrayList<Instruction>? = null
        for (i in nodes.lastIndex downTo 0) {
            val node = nodes[i]
            if (i > 0) loadStackPrepend(node.inputStack, node.printer, mt)

            // append label set
            when (node) {
                is ReturnNode -> {}
                is SequenceNode -> {
                    node.printer
                        .append(i32Const(node.next.index))
                        .append(caseVariable.setter)
                    storeStackAppend(node.outputStack, node.printer, mt)
                }
                is BranchNode -> {
                    val ifTrue = arrayListOf<Instruction>(i32Const(node.ifTrue.index))
                    val ifFalse = arrayListOf<Instruction>(i32Const(node.ifFalse.index))
                    node.printer
                        .append(IfBranch(ifTrue, ifFalse, emptyList(), listOf("int")))
                        .append(caseVariable.setter)
                    storeStackAppend(node.outputStack, node.printer, mt)
                }
                else -> throw NotImplementedError()
            }

            // append case
            lastCase = if (lastCase == null) {
                node.printer.instrs
            } else {
                arrayListOf(
                    caseVariable.getter,
                    i32Const(node.index), I32EQ,
                    IfBranch(node.printer.instrs, lastCase)
                )
            }
        }

        lastCase!!

        val loopInstr = LoopInstr("stack${mt.nextLoopIndex++}", lastCase, emptyList(), emptyList())
        lastCase.add(Jump(loopInstr))

        val result = Builder(4)
        result.append(i32Const0).append(caseVariable.setter)
        result.append(loopInstr)
        result.append(Unreachable)

        val firstNode = nodes.first()
        nodes.clear()
        nodes.add(ReturnNode(result).apply {
            inputStack = firstNode.inputStack
            outputStack = emptyList()
        })
        return
    }
}