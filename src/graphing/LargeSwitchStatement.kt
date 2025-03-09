package graphing

import graphing.SaveGraph.graphId
import graphing.SaveGraph.saveGraph
import graphing.StructuralAnalysis.Companion.comments
import graphing.StructuralAnalysis.Companion.printState
import graphing.StructuralAnalysis.Companion.renumber
import me.anno.utils.assertions.assertFail
import me.anno.utils.assertions.assertTrue
import translator.MethodTranslator
import utils.Builder
import utils.WASMTypes.i32
import wasm.instr.*
import wasm.instr.Const.Companion.i32Const
import wasm.instr.Instructions.Unreachable

object LargeSwitchStatement {

    private fun filterFirstIsLinear(nodes: List<GraphingNode>): List<GraphingNode> {
        val firstNode = nodes.first() as SequenceNode
        // make sure that the node that follows the first one is the first node of the "tree"
        //  otherwise, our C++ logic "skips" the first goto, which is terrible
        val nextNode = firstNode.next
        val dst = ArrayList<GraphingNode>(nodes.size - 1)
        dst.add(nextNode)
        for (i in 1 until nodes.size) {
            val node = nodes[i]
            if (node != nextNode) dst.add(node)
        }
        return dst
    }

    fun createLargeSwitchStatement2(sa: StructuralAnalysis): Builder {
        val nodes0 = sa.nodes

        val firstNode = nodes0.first()
        val firstIsLinear = firstNode !is BranchNode && firstNode.inputs.isEmpty()
        val nodes = if (firstIsLinear) filterFirstIsLinear(nodes0) else nodes0

        if (sa.methodTranslator.isLookingAtSpecial) {
            printState(nodes0, "Before GraphID")
        }

        // create graph id
        // to do store image of graph based on id
        val graphId = graphId(sa)
        if (true) saveGraph(graphId, sa)

        if (sa.methodTranslator.isLookingAtSpecial) {
            println(graphId)
        }

        renumber(nodes)
        if (firstIsLinear) firstNode.index = nodes.size

        val label = "lbl"
        val lblSet = LocalSet(label)

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
                    storeStack(node, sa.methodTranslator)
                }
                is SequenceNode -> {
                    // set end label
                    val next = node.next.index
                    printer1.append(i32Const(next)).append(lblSet)
                    // store stack
                    storeStack(node, sa.methodTranslator)
                }
                else -> assertFail()
            }
        }

        val loopName = "b${sa.methodTranslator.nextLoopIndex++}"
        val loopInstr = LoopInstr(loopName, emptyList(), emptyList(), emptyList())
        val jump = Jump(loopInstr)

        val printer = Builder()
        if (firstIsLinear) {
            if (comments) printer.comment("execute -1")
            finishBlock(firstNode)
            printer.append(firstNode.printer)
            assertTrue(printer.lastOrNull()?.isReturning() != true)
        } else {
            printer
                .append(i32Const(nodes.first().index))
                .append(lblSet)
        }

        printer.comment("#$graphId")

        fun appendBlock(node: GraphingNode) {
            // load stack
            loadStack(node, sa.methodTranslator)
            // execute
            if (comments) node.printer.prepend(Comment("execute ${node.index}"))
            finishBlock(node)
            // close block
            node.printer.append(jump)
        }

        for (node in nodes) {
            appendBlock(node)
        }

        // good like that???
        val switch = SwitchCase(label, nodes.map { it.printer.instrs }, emptyList(), emptyList())
        loopInstr.body = listOf(switch)
        printer.append(loopInstr)

        // close loop
        printer.append(Unreachable)

        return printer
    }

    fun loadStack(node: GraphingNode, mt: MethodTranslator) {
        loadStack(node.inputStack, node.printer, mt)
    }

    fun loadStack(inputs: List<String>, printer: Builder, mt: MethodTranslator) {
        if (inputs.isEmpty()) return
        val pre = Builder()
        if (comments) pre.comment("load stack $inputs")
        for (idx in inputs.indices) {
            val type = inputs[idx]
            pre.append(LocalGet(mt.variables.getStackVarName(idx, type)))
        }
        printer.prepend(pre)
    }

    fun storeStack(node: GraphingNode, mt: MethodTranslator) {
        storeStack(node.printer, node.outputStack, mt)
    }

    fun storeStack(printer: Builder, outputs: List<String>, mt: MethodTranslator) {
        if (outputs.isEmpty()) return
        if (comments) printer.comment("store stack $outputs")
        for ((idx, type) in outputs.withIndex().reversed()) {
            printer.append(LocalSet(mt.variables.getStackVarName(idx, type)))
        }
    }
}