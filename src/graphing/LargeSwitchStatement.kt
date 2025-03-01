package graphing

import graphing.SaveGraph.graphId
import graphing.SaveGraph.saveGraph
import graphing.StructuralAnalysis.Companion.comments
import graphing.StructuralAnalysis.Companion.printState
import graphing.StructuralAnalysis.Companion.renumber
import me.anno.utils.assertions.assertFail
import utils.Builder
import utils.i32
import wasm.instr.*
import wasm.instr.Const.Companion.i32Const
import wasm.instr.Instructions.Unreachable

object LargeSwitchStatement {

    fun createLargeSwitchStatement2(sa: StructuralAnalysis): Builder {
        val nodes0 = sa.nodes
        val firstNode = nodes0.first()
        val firstIsLinear = !firstNode.isBranch && firstNode.inputs.isEmpty()
        val nodes = if (firstIsLinear) nodes0.subList(1, nodes0.size) else nodes0

        renumber(nodes)
        if (firstIsLinear) firstNode.index = nodes.size

        if (sa.isLookingAtSpecial) {
            printState(nodes0, "Before GraphID")
        }

        // create graph id
        // to do store image of graph based on id
        val graphId = graphId(sa)
        if (true) saveGraph(graphId, sa)

        if (sa.isLookingAtSpecial) {
            println(graphId)
        }

        val lblName = "lbl"
        val lblSet = LocalSet(lblName)

        // create large switch-case-statement
        // https://musteresel.github.io/posts/2020/01/webassembly-text-br_table-example.html

        fun finishBlock(node: GraphingNode) {
            val printer1 = node.printer
            when (node) {
                is ReturnNode -> printer1.append(Unreachable)
                is BranchNode -> {
                    // set end label
                    val trueIndex = node.ifTrue.index
                    val falseIndex = node.ifFalse.index
                    printer1.append(
                        IfBranch(
                            listOf(i32Const(trueIndex)),
                            listOf(i32Const(falseIndex)),
                            emptyList(), listOf(i32)
                        )
                    ).append(lblSet)
                    // store stack
                    storeStack(node, sa)
                }
                is SequenceNode -> {
                    // set end label
                    val next = node.next.index
                    printer1.append(i32Const(next)).append(lblSet)
                    // store stack
                    storeStack(node, sa)
                }
                else -> assertFail()
            }
        }

        val loopIdx = sa.loopIndex++
        val loopName = "b$loopIdx"

        val printer = Builder()
        if (firstIsLinear) {
            if (comments) printer.comment("execute -1")
            finishBlock(firstNode)
            printer.append(firstNode.printer)
        } else {
            printer
                .append(i32Const(nodes.first().index))
                .append(lblSet)
        }

        printer.comment("#$graphId")

        fun appendBlock(node: GraphingNode) {
            // load stack
            loadStack(node, sa)
            // execute
            if (comments) node.printer.prepend(Comment("execute ${node.index}"))
            finishBlock(node)
            // close block
            node.printer.append(Jump(loopName))
        }

        for (node in nodes) {
            appendBlock(node)
        }

        // good like that???
        val switch = SwitchCase(lblName, nodes.map { it.printer.instrs })
        printer.append(LoopInstr(loopName, listOf(switch), emptyList()))

        // close loop
        printer.append(Unreachable)

        return printer
    }

    fun loadStack(node: GraphingNode, sa: StructuralAnalysis) {
        val inputs = node.inputStack
        if (inputs.isEmpty()) return
        val pre = Builder()
        if (comments) pre.comment("load stack $inputs")
        for (idx in inputs.indices) {
            val type = inputs[idx]
            pre.append(LocalGet(sa.getStackVarName(idx, type)))
        }
        node.printer.prepend(pre)
    }

    fun storeStack(node: GraphingNode, sa: StructuralAnalysis) {
        val outputs = node.outputStack
        if (outputs.isEmpty()) return
        val printer = node.printer
        if (comments) printer.comment("store stack $outputs")
        for ((idx, type) in outputs.withIndex().reversed()) {
            printer.append(LocalSet(sa.getStackVarName(idx, type)))
        }
    }
}