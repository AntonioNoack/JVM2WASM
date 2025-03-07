package graphing

import graphing.LargeSwitchStatement.loadStack
import graphing.LargeSwitchStatement.storeStack
import graphing.SolveLinearTree.trySolveLinearTree
import graphing.StackValidator.validateStack
import graphing.StructuralAnalysis.Companion.printState
import graphing.StructuralAnalysis.Companion.renumber
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.structures.lists.Lists.partition1
import me.anno.utils.structures.lists.Lists.swap
import me.anno.utils.structures.lists.TopologicalSort
import utils.Builder
import wasm.instr.Const.Companion.i32Const0
import wasm.instr.Const.Companion.i32Const1
import wasm.instr.IfBranch
import wasm.instr.Instructions.Unreachable
import wasm.instr.Jump
import wasm.instr.LoopInstr

/**
 * Try to find a node, which has multiple inputs, is cyclic, but after cutting all connections TO it, the remainder after that node is non-cyclic.
 * */
object ExtractBigLoop {

    fun tryExtractBigLoop(sa: StructuralAnalysis): Boolean {
        return false // not yet properly implemented
        val startNodes = findBigLoop(sa)
        if (startNodes != null && shouldReplaceStartNodes(startNodes)) {
            createMergedCode(sa, startNodes)
            validateStack(sa.nodes, sa.methodTranslator)
            return true
        } else return false
    }

    private class BigLoop(
        val treeNodes: Set<GraphingNode>,
        val loopStart: GraphingNode
    )

    private fun findBigLoop(sa: StructuralAnalysis): BigLoop? {
        // maximize
        //  start -> loop - ... > loop, such that inside loop nothing is recursive
        val nodes = sa.nodes
        return nodes.indices.mapNotNull {
            val loopStart = nodes[it]
            findBigLoopFrom(loopStart, sa)
        }.maxByOrNull { it.treeNodes.size }
    }

    private fun findBigLoopFrom(
        loopStart: GraphingNode,
        sa: StructuralAnalysis
    ): BigLoop? {

        // todo support inputStacks using helper nodes
        if (loopStart.inputStack.isNotEmpty()) return null

        val treeNodes = ArrayList(sa.nodes)
        val sorter = object : TopologicalSort<GraphingNode, ArrayList<GraphingNode>>(treeNodes) {
            override fun visitDependencies(node: GraphingNode): Boolean {
                return node.outputs.any2 { nextNode ->
                    if (nextNode == loopStart) false // links to start are ignored
                    else visit(nextNode)
                }
            }
        }
        treeNodes.clear()

        val hasCycle = sorter.visit(loopStart)
        if (hasCycle) return null

        val firstNode = sa.nodes.first()
        if (loopStart != firstNode && firstNode in treeNodes) {
            // if everything can be a big loop,
            // the loop must start at firstNode, otherwise we get an incorrect start...
            return null
        }

        val startNodesSet = treeNodes.toHashSet()
        val hasLinkFromEndToStart = treeNodes.any2 { treeNode ->
            treeNode != loopStart && treeNode.inputs.any { it !in startNodesSet }
        }
        if (hasLinkFromEndToStart) return null
        return BigLoop(startNodesSet, loopStart)
    }

    private fun shouldReplaceStartNodes(subset: BigLoop): Boolean {
        return when (subset.treeNodes.size) {
            0, 1 -> false
            else -> true
        }
    }

    private fun validateNodes(sa: StructuralAnalysis, treeNodes: Set<GraphingNode>, loopStart: GraphingNode) {
        assertTrue(loopStart in treeNodes)
        for (node in sa.nodes) {
            when (node) {
                loopStart -> {
                    // nothing to be checked
                }
                in treeNodes -> {
                    assertTrue(node.inputs.all { it in treeNodes })
                    assertTrue(node.outputs.all { it in treeNodes })
                }
                else -> {
                    assertTrue(node.inputs.all { it !in treeNodes }) // must not be middle
                    assertTrue(node.outputs.all { it !in treeNodes || it == loopStart }) // can be middle
                }
            }
        }
    }

    private fun createMergedCode(sa: StructuralAnalysis, bigLoop: BigLoop) {

        // todo this hasn't been implemented properly!!!

        val print = true
        val validate = true

        val treeNodes = bigLoop.treeNodes
        val loopStart = bigLoop.loopStart

        val firstNode = sa.nodes.first()

        // these are necessary!!!
        sa.nodes.partition1 { it !in treeNodes } // put startNodes first
        sa.nodes.swap(sa.nodes.indexOf(loopStart), sa.nodes.size - treeNodes.size) // middle comes after that
        assertEquals(sa.nodes.distinct().size, sa.nodes.size)
        assertEquals(firstNode, sa.nodes.first())
        renumber(sa.nodes)

        if (print) {
            println()
            println("------------------------------------------------------")
            println(
                "BigLoop, " +
                        "start: 0-${loopStart.index - 1}, " +
                        "loop: ${loopStart.index}, " +
                        "tree: ${loopStart.index + 1}-${sa.nodes.lastIndex}"
            )
            printState(sa.nodes, "MergeCode")
        }

        if (validate) validateNodes(sa, treeNodes, loopStart)

        val mt = sa.methodTranslator
        val loopLabel = "bigLoop${mt.bigLoopExtractorIndex++}"
        // val jumpInstr = Jump(loopLabel)

        if (validate) validateStack(sa.nodes, sa.methodTranslator)

        val needsStackHelperNodes =
            loopStart.inputStack.isNotEmpty()

        var stackLoadNode: SequenceNode? = null
        var stackStoreNode: SequenceNode? = null
        if (needsStackHelperNodes) {
            // we need a stack-load-helper-node
            val stackLoadBuilder = Builder()
            loadStack(loopStart.inputStack, stackLoadBuilder, mt)
            stackLoadNode = SequenceNode(stackLoadBuilder, loopStart)
            stackLoadNode.inputStack = emptyList()
            stackLoadNode.outputStack = loopStart.inputStack
            stackLoadNode.index = sa.nodes.size
            sa.nodes.add(stackLoadNode) // is this a good place???

            // we also need a stack-store-helper node
            val stackStoreBuilder = Builder()
            storeStack(stackStoreBuilder, loopStart.inputStack, mt)
            stackStoreNode = SequenceNode(stackStoreBuilder, stackLoadNode)
            stackStoreNode.index = sa.nodes.size
            sa.nodes.add(stackStoreNode)

            for (node in sa.nodes) {
                // todo this is missing stackStoreNodes for all treeNodes
                sa.replaceOutputs(loopStart, if (node in treeNodes) stackLoadNode else stackStoreNode)
            }
            TODO("verify link structure")
        }

        // todo replace links to loopStart with goto loadStackNode/loopStart
        // todo unlink tree-nodes
        // todo remove tree-nodes from sa.nodes
        // todo build tree-nodes into simple instructions
        // todo replace loadStack/loopStart with actual loop (careful! loopStart is part of the tree, so no longer in sa.nodes)

        TODO()
    }
}