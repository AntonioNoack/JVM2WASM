package translator

import graphing.BranchNode
import graphing.GraphingNode
import graphing.ReturnNode
import graphing.SequenceNode
import me.anno.utils.algorithms.Recursion
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.lists.Lists.all2
import me.anno.utils.structures.lists.Lists.createArrayList
import translator.JavaTypes.convertTypesToWASM
import utils.Builder
import wasm.instr.Comment

class TranslatorNode(val label: Int) {

    var ifTrue: Int? = null
    var ifFalse: TranslatorNode? = null

    var isAlwaysTrue = false
    var isReturn = false

    val next get() = if (isAlwaysTrue) ifTrue else ifFalse?.label
    val isBranch get() = ifTrue != null && ifFalse != null && !isAlwaysTrue

    var inputStack: List<String> = emptyList()
    var outputStack: List<String> = emptyList()

    val printer = Builder()

    fun toString(mapper: (label: Int?) -> String): String {
        val name = mapper(label)
        return if (isAlwaysTrue) {
            "[$name -> ${mapper(ifTrue)}]"
        } else if (isReturn) {
            "[$name -> exit]"
        } else if (ifTrue != null) {
            "[$name ? ${mapper(ifTrue!!)} : ${mapper(ifFalse!!.label)}]"
        } else if (ifFalse == null) {
            "[$name -?> void]"
        } else {
            "[$name -> ${mapper(ifFalse!!.label)}]"
        }
    }

    override fun toString(): String {
        return toString { it!!.toString() }
    }

    companion object {

        object VoidNode : GraphingNode(Builder(0)) {
            override val outputs: List<GraphingNode> = emptyList()
        }

        fun convertNodes(nodes: List<TranslatorNode>): ArrayList<GraphingNode> {
            val newNodes = createArrayList(nodes.size) { i ->
                val node = nodes[i]
                when {
                    node.isReturn -> ReturnNode(node.printer)
                    node.isBranch -> BranchNode(node.printer)
                    node.next != null -> SequenceNode(node.printer)
                    else -> {
                        assertTrue(node.printer.instrs.all2 { it is Comment })
                        VoidNode
                    }
                }
            }

            val maxLabel = nodes.maxOf { it.label }
            val labelToNode = arrayOfNulls<GraphingNode>(maxLabel + 1)
            for (i in nodes.indices) {
                labelToNode[nodes[i].label] = newNodes[i]
            }

            // add input stack and output stack
            for (i in nodes.indices) {
                val node = nodes[i]
                val newNode = newNodes[i]
                newNode.inputStack = node.inputStack
                newNode.outputStack = node.outputStack
            }

            // link all new nodes
            for (i in nodes.indices) {
                val node = nodes[i]
                when (val newNode = newNodes[i]) {
                    is SequenceNode -> {
                        newNode.next = labelToNode[node.next!!]!!
                    }
                    is BranchNode -> {
                        newNode.ifTrue = labelToNode[node.ifTrue!!]!!
                        newNode.ifFalse = labelToNode[node.ifFalse!!.label]!!
                    }
                }
            }

            // remove not-reached nodes
            val reached = Int.MAX_VALUE
            Recursion.processRecursive(newNodes.first()) { node, remaining ->
                node.index = reached
                for (next in node.outputs) {
                    if (next.index != reached) remaining.add(next)
                }
            }

            newNodes.removeIf { it is VoidNode || it.index != reached }

            for (i in newNodes.indices) {
                newNodes[i].index = i
            }

            return newNodes
        }
    }
}