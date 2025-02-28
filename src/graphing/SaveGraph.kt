package graphing

import graphing.StructuralAnalysis.Companion.equalPairs
import graphing.StructuralAnalysis.Companion.folder
import me.anno.io.Streams.writeString
import org.objectweb.asm.Label
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

    private fun normalizeGraph(nodes: MutableList<Node>, labelToNode: Map<Label, Node>) {
        for (node in nodes) {
            if (node.isBranch) {
                val ifTrue = labelToNode[node.ifTrue]!!
                val ifFalse = node.ifFalse!!
                if (ifTrue.index > ifFalse.index) {
                    swapBranches(node, ifTrue, ifFalse)
                }
            }
        }
    }

    private fun swapBranches(node: Node, ifTrue: Node, ifFalse: Node) {
        val printer = node.printer
        // swap branches :)
        node.ifTrue = ifFalse.label
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
        if (false) normalizeGraph(sa.nodes, sa.labelToNode)
        val nodes = sa.nodes
        val builder = StringBuilder(nodes.size * 5)
        for (node in nodes) {
            builder.append(
                when {
                    node.isAlwaysTrue -> 'T'
                    node.isReturn -> 'R'
                    node.isBranch -> 'B'
                    else -> 'N'
                }
            )
            val a = sa.labelToNode[node.ifTrue]?.index
            val b = node.ifFalse?.index
            if (a != null) builder.append(a)
            if (b != null) {
                if (a != null) builder.append('-')
                builder.append(b)
            }
        }
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
            sa.printState { builder.append(it).append('\n') }
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