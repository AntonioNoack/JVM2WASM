package utils

import graphing.GraphingNode
import graphing.StructuralAnalysis.Companion.renumberForReading
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.structures.lists.Lists.createList
import wasm.instr.Call
import wasm.instr.Const

/**
 * eliminates roughly a third of all static-init-calls, which is a nice-to-have (13600 -> 8900)
 * */
object StaticInitOptimizer {

    fun optimizeStaticInit(nodes: MutableList<GraphingNode>) {
        // breadth first search for node order
        renumberForReading(nodes)
        // go through each node and collect which static-init was called already
        // on branches, go both paths and 'AND' their result
        val calledInit = createList(nodes.size) { HashSet<String>() }
        for (node in nodes) {
            optimizeStaticInit(node, calledInit)
        }
    }

    fun optimizeStaticInit(node: GraphingNode, calledInit: List<HashSet<String>>) {
        // collect info on input nodes: AND between all inputs
        combineStaticInit(node, calledInit)
        val calledInitI = calledInit[node.index]
        val instructions = node.printer.instrs
        var i = 0
        while (i < instructions.size) {
            val instr = instructions.getOrNull(i) ?: break
            if (instr is Call && isStaticInit(instr)) {
                if (!calledInitI.add(instr.name)) {
                    // remove this static-init-call
                    if (i >= 2 && i + 1 < instructions.size &&
                        instructions[i - 2] is Const &&
                        instructions[i - 1] == Call.stackPush &&
                        instructions[i + 1] == Call.stackPop
                    ) {
                        // if begins with stackPush/stackPop, remove them, too
                        instructions.subList(i - 2, i + 2).clear()
                        i -= 2 // go back two steps, because we deleted two elements in front of us
                    } else instructions.removeAt(i)
                } else i++
            } else i++
        }
    }

    private fun combineStaticInit(node: GraphingNode, calledInit: List<HashSet<String>>) {
        // collect info on input nodes: AND between all inputs
        val given = calledInit[node.index]
        assertTrue(given.isEmpty())
        val inputs = node.inputs.map { calledInit[it.index] }
        if (inputs.any2 { it.isEmpty() } || inputs.isEmpty()) return
        given.addAll(inputs.first())
        for (i in 1 until inputs.size) {
            given.retainAll(inputs[i])
        }
    }

    private fun isStaticInit(instruction: Call): Boolean {
        return instruction.name.startsWith("static_")
    }

}