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
import utils.WASMTypes.i32
import wasm.instr.Const.Companion.i32Const0
import wasm.instr.Const.Companion.i32Const1
import wasm.instr.IfBranch
import wasm.instr.Instructions.Unreachable
import wasm.instr.Jump
import wasm.instr.LoopInstr

/**
 * Try to find a middle node, that splits the graph into a first non-cyclic and a second cyclic part.
 * */
object ExtractStartNodes {

    fun tryExtractStart(sa: StructuralAnalysis): Boolean {
        val startNodes = findStartNodes(sa)
        if (startNodes != null && shouldReplaceStartNodes(startNodes)) {
            createMergedCode(sa, startNodes)
            validateStack(sa.nodes, sa.methodTranslator)
            return true
        } else return false
    }

    private class NonRecursiveSubset(
        val nodes: Set<GraphingNode>,
        val middle: GraphingNode
    )

    private fun findStartNodes(sa: StructuralAnalysis): NonRecursiveSubset? {
        // maximize
        //  start -> mid -> end, on |start -> mid|, such that start -> mid is non-recursive
        val nodes = sa.nodes
        val start = nodes.first()
        return nodes.indices.mapNotNull {
            val middle = nodes[it]
            getNonRecursiveSubset(start, middle, sa)
        }.maxByOrNull { it.nodes.size }
    }

    private fun getNonRecursiveSubset(
        start: GraphingNode, middle: GraphingNode,
        sa: StructuralAnalysis
    ): NonRecursiveSubset? {
        val treeNodes = ArrayList(sa.nodes)
        val sorter = object : TopologicalSort<GraphingNode, ArrayList<GraphingNode>>(treeNodes) {
            override fun visitDependencies(node: GraphingNode): Boolean {
                if (node == middle) return true // should not happen!
                return node.outputs.any2 { nextNode ->
                    if (nextNode == middle) false // don't go onto middle!
                    else visit(nextNode)
                }
            }
        }
        treeNodes.clear()
        val hasCycle = sorter.visit(start)
        if (hasCycle) return null
        val isAllNodes = treeNodes.size >= sa.nodes.size - 1
        if (isAllNodes) return null
        val startNodesSet = treeNodes.toHashSet()
        val hasLinkFromEndToStart = treeNodes.any2 { startNode ->
            startNode.inputs.any { it !in startNodesSet }
        }
        if (hasLinkFromEndToStart) return null
        return NonRecursiveSubset(startNodesSet, middle)
    }

    private fun shouldReplaceStartNodes(subset: NonRecursiveSubset): Boolean {
        return when (subset.nodes.size) {
            0, 1 -> false
            else -> true
        }
    }

    private fun validateNodes(sa: StructuralAnalysis, startNodes: Set<GraphingNode>, middle: GraphingNode) {
        assertTrue(startNodes.size < sa.nodes.size - 1)
        assertTrue(sa.nodes.first() in startNodes)
        assertTrue(middle !in startNodes)
        for (node in sa.nodes) {
            when (node) {
                middle -> {
                    assertTrue(node.outputs.all { it !in startNodes })
                }
                in startNodes -> {
                    assertTrue(node.inputs.all { it in startNodes })
                    assertTrue(node.outputs.all { it in startNodes || it == middle })
                }
                else -> {
                    assertTrue(node.inputs.all { it !in startNodes }) // may be middle
                    assertTrue(node.outputs.all { it !in startNodes }) // middle is allowed to be recursive
                }
            }
        }
    }

    private fun createMergedCode(sa: StructuralAnalysis, subset: NonRecursiveSubset) {

        val print = sa.methodTranslator.isLookingAtSpecial
        val validate = true

        val startNodes = subset.nodes
        val middle = subset.middle

        val firstNode = sa.nodes.first()
        val graphInput = firstNode.inputStack

        // these are necessary!!!
        sa.nodes.partition1 { it in startNodes } // put startNodes first
        sa.nodes.swap(sa.nodes.indexOf(middle), startNodes.size) // middle comes after that
        assertEquals(firstNode, sa.nodes.first())
        renumber(sa.nodes)

        if (print) {
            println()
            println("------------------------------------------------------")
            println(
                "ExtractStart, " +
                        "start: 0-${startNodes.size - 1}, " +
                        "middle: ${middle.index}, " +
                        "end: ${middle.index + 1}-${sa.nodes.lastIndex}"
            )
            printState(sa.nodes, "MergeCode")
        }

        if (validate) validateNodes(sa, startNodes, middle)

        val mt = sa.methodTranslator
        val firstRunLabel = "firstRunS${mt.startNodeExtractorIndex++}"
        val firstRunVariable = mt.variables.addLocalVariable("${firstRunLabel}v", i32, "I")
        val loopInstr = LoopInstr(firstRunLabel, emptyList(), emptyList(), emptyList())
        val jumpInstr = Jump(loopInstr)

        if (validate) validateStack(sa.nodes, sa.methodTranslator)

        // replace goto-end-node to jumps to it
        val middlePlusEnd = HashSet<GraphingNode>(sa.nodes.size - startNodes.size + 1)
        for (node in sa.nodes) {
            if (node !in startNodes) middlePlusEnd.add(node)
        }
        ExtractEndNodes.replaceGotoEndNodes(sa, validate, print, middlePlusEnd) { _, next ->
            val builder = Builder(3)
            storeStack(next.inputStack, builder, sa.methodTranslator)
            builder.append(jumpInstr)
            builder
        }
        // we need to recreate the startNodes-data, because some of these nodes will have been replaced
        //  to make this work, it is important that the list is partitioned properly!!
        // copy is needed, subList is just a view
        val startNodesList0 = sa.nodes.subList(0, startNodes.size)
        val startNodesList = ArrayList(startNodesList0)
        val startNodes1 = startNodesList.toHashSet()
        startNodesList0.clear() // remove start nodes from sa.nodes

        middle.inputs.removeIf { it in startNodes1 } // unlink to-end-nodes
        if (print) printState(sa.nodes, "Removed start nodes")

        if (validate) {
            if (print) {
                printState(startNodesList, "Validating StartNodes")
                printState(sa.nodes, "Validating End/MiddleNodes")
            }
            // validate that startNodes no longer has any links to middle/endNodes
            for (node in startNodes1) {
                assertTrue(node.inputs.all { it in startNodes1 })
                assertTrue(node.outputs.all { it in startNodes1 }) {
                    "$node has invalid output(s): ${node.outputs.filter { it !in startNodes1 }}"
                }
            }
            assertTrue(middle.inputs.none { it in startNodes1 })
            assertTrue(middle.outputs.none { it in startNodes1 })
            for (node in sa.nodes) {
                assertTrue(node.inputs.none { it in startNodes1 })
                assertTrue(node.outputs.none { it in startNodes1 })
            }
        }

        assertTrue(trySolveLinearTree(startNodesList, sa.methodTranslator, true, emptyMap()))
        assertEquals(1, startNodesList.size)
        val treeRoot = startNodesList.first().printer
        treeRoot.prepend(listOf(i32Const0, firstRunVariable.setter)) // don't run a second time
        treeRoot.append(Unreachable) // end of it should be unreachable

        loopInstr.body = listOf(
            firstRunVariable.getter,
            IfBranch(
                treeRoot.instrs, // anything after is unreachable
                emptyList(), // break out of the loop
                emptyList(), emptyList()
            )
        )

        val dst = Builder()
        dst.append(i32Const1).append(firstRunVariable.setter)
        dst.append(loopInstr)
        loadStack(middle.inputStack, dst, sa.methodTranslator)

        val newFirstNode = SequenceNode(dst, middle)
        newFirstNode.inputStack = graphInput
        newFirstNode.outputStack = middle.inputStack
        middle.inputs.add(newFirstNode)
        sa.nodes.add(0, newFirstNode)

        if (print) {
            renumber(sa.nodes)
            printState(sa.nodes, "ExtractStartNodes")
        }

        if (validate) {
            validateStack(sa.nodes, sa.methodTranslator)
        }

    }
}