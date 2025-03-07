package graphing

import graphing.StructuralAnalysis.Companion.equalPairs
import graphing.StructuralAnalysis.Companion.folder
import graphing.StructuralAnalysis.Companion.printState
import me.anno.io.Streams.writeString
import me.anno.utils.assertions.assertFalse
import utils.methodName
import wasm.instr.Instructions.I32EQZ
import java.io.File
import java.io.FileOutputStream

object SaveGraph {

    private val textCache = HashMap<String, HashSet<String>>()
    private fun getFileLines(graphId: String): HashSet<String> {
        return textCache.getOrPut(graphId) {
            val file = File(folder, graphId)
            if (file.exists()) file.readText().split('\n').toHashSet()
            else HashSet()
        }
    }

    private fun normalizeGraph(nodes: MutableList<GraphingNode>) {
        for (i in nodes.indices) {
            val node = nodes[i]
            if (node is BranchNode) {
                val ifTrue = node.ifTrue
                val ifFalse = node.ifFalse
                if (ifTrue.index > ifFalse.index) {
                    swapBranches(node, ifTrue, ifFalse)
                }
            }
        }
    }

    private fun swapBranches(node: BranchNode, ifTrue: GraphingNode, ifFalse: GraphingNode) {
        val printer = node.printer
        // swap branches :)
        node.ifTrue = ifFalse
        node.ifFalse = ifTrue
        // swap conditional
        val lastInstr = printer.instrs.lastOrNull()
        val invInstr = equalPairs[lastInstr]
        if (invInstr != null) {
            printer.instrs.removeLast()
            printer.append(invInstr)
        } else {
            printer.append(I32EQZ)
        }
    }

    fun graphId(sa: StructuralAnalysis): String {
        // return methodName(sig).shorten(50).toString() + ".txt"
        StructuralAnalysis.renumber2(sa.nodes)
        if (false) normalizeGraph(sa.nodes)
        val nodes = sa.nodes
        val builder = StringBuilder(nodes.size * 5)
        for (node in nodes) {
            builder.append(
                when (node) {
                    is ReturnNode -> 'R'
                    is BranchNode -> 'B'
                    else -> 'N'
                }
            )
            val nextNodes = node.outputs
            for (i in nextNodes.indices) {
                if (i > 0) builder.append('-')
                builder.append(nextNodes[i].index)
            }
        }
        assertFalse(
            builder.startsWith("R"),
            "First node shouldn't be return in LargeSwitchStatement, because that would be trivial"
        )
        val maxLength = 80
        if (builder.length > maxLength) {
            val hash = builder.toString().hashCode()
            val extra = builder.substring(0, maxLength - 8)
            builder.clear()
            builder.append(extra)
            builder.append('-')
            builder.append(hash.toUInt().toString(16))
        }
        builder.append(".txt")
        return builder.toString()
    }

    fun saveGraph(graphId: String, sa: StructuralAnalysis) {
        val fileLines = getFileLines(graphId)
        if (fileLines.isEmpty()) {
            val builder = StringBuilder()
            builder.append(sa.sig).append('\n')
            builder.append(methodName(sa.sig)).append('\n')
            printState(sa.nodes) { builder.append(it).append('\n') }
            File(folder, graphId).writeText(builder.toString())
        } else {
            val sigStr = sa.sig.toString()
            if (sigStr !in fileLines) {
                FileOutputStream(File(folder, graphId), true).use {
                    it.writeString(sigStr)
                    it.write('\n'.code)
                    it.writeString(methodName(sa.sig))
                    it.write('\n'.code)
                }
            }
        }
    }

}