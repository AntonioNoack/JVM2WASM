package graphing

import graphing.ExtractEndNodes.replaceGotoEndNode
import graphing.ExtractEndNodes.solve
import graphing.LargeSwitchStatement.loadStack
import graphing.LargeSwitchStatement.storeStack
import graphing.StackValidator.validateInputOutputStacks
import graphing.StackValidator.validateInputs
import graphing.StackValidator.validateStack
import graphing.StructuralAnalysis.Companion.printState
import graphing.StructuralAnalysis.Companion.renumber
import me.anno.utils.assertions.*
import me.anno.utils.structures.Recursion
import me.anno.utils.structures.lists.Lists.partition1
import me.anno.utils.structures.lists.Lists.swap
import translator.MethodTranslator
import utils.Builder
import wasm.instr.Instructions.Unreachable
import wasm.instr.Jump
import wasm.instr.LoopInstr
import kotlin.math.abs

/**
 * ideal: half/half-split
 * must-haves:
 * - at least one node before the graph
 * - more than one node in the graph
 * - all startNodes terminate in loopStart
 * - no other connections between treeNodes and startNodes
 * */
object ExtractBigLoop {

    fun tryExtractBigLoop(sa: StructuralAnalysis): Boolean {
        // return false // not yet properly implemented
        val startNodes = findBigLoop(sa)
        if (startNodes != null && shouldReplaceStartNodes(startNodes)) {
            createMergedCode(sa, startNodes)
            validateStack(sa.nodes, sa.methodTranslator)
            return true
        } else return false
    }

    private class BigLoop(
        val inLoopNodes: Set<GraphingNode>,
        val loopStart: GraphingNode
    )

    private fun findBigLoop(sa: StructuralAnalysis): BigLoop? {
        val nodes = sa.nodes
        return nodes.indices.mapNotNull {
            val loopStart = nodes[it]
            findBigLoopFrom(loopStart, sa)
        }.minByOrNull {
            // the ideal split is in the middle
            abs(nodes.size.shr(1) - it.inLoopNodes.size)
        }
    }

    private fun findBigLoopFrom(loopStart: GraphingNode, sa: StructuralAnalysis): BigLoop? {

        if (loopStart is ReturnNode) return null
        if (loopStart.inputs.size < 2) return null // not a splitting node
        // we don't want to extract a single node with this much effort
        if (loopStart is SequenceNode && loopStart.next == loopStart) return null

        val loopNodes = Recursion.collectRecursive(loopStart) { node, remaining ->
            remaining.addAll(node.outputs)
        }

        val firstNode = sa.nodes.first()
        if (loopStart != firstNode && firstNode in loopNodes) {
            // if firstNode is inside the loop, it must be the start
            return null
        }

        val loopNodesSet = loopNodes.toHashSet()
        // loop to start is covered by recursive discovery
        val hasLinkFromStartToLoop = loopNodes.any { loopNode ->
            loopNode != loopStart && loopNode.inputs.any { it !in loopNodesSet }
        }
        if (hasLinkFromStartToLoop) return null
        return BigLoop(loopNodesSet, loopStart)
    }

    private fun shouldReplaceStartNodes(subset: BigLoop): Boolean {
        return when (subset.inLoopNodes.size) {
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

    private fun createMergedCode(sa: StructuralAnalysis, subset: BigLoop) {

        val print = true || sa.methodTranslator.isLookingAtSpecial
        val validate = true

        val loopStart = subset.loopStart
        val inLoopNodes = subset.inLoopNodes
        assertTrue(loopStart in inLoopNodes)
        assertFalse(loopStart in loopStart.inputs)

        val firstNode = sa.nodes.first()
        val startNodesSize = sa.nodes.size - inLoopNodes.size

        // these are necessary!!!
        sa.nodes.partition1 { it !in inLoopNodes } // put startNodes first
        sa.nodes.swap(sa.nodes.indexOf(loopStart), startNodesSize) // middle comes after that
        assertEquals(firstNode, sa.nodes.first())
        renumber(sa.nodes)

        if (print) {
            println()
            println("------------------------------------------------------")
            println(
                "ExtractStart, " +
                        "start: 0-${startNodesSize - 1}, " +
                        "loopStart: ${loopStart.index}, " +
                        "inLoop: ${loopStart.index + 1}-${sa.nodes.lastIndex}"
            )
            printState(sa.nodes, "MergeCode")
        }

        if (validate) validateNodes(sa, inLoopNodes, loopStart)

        val mt = sa.methodTranslator
        val loopLabel = "bigLoop${mt.bigLoopExtractorIndex++}"
        val loopInstr = LoopInstr(loopLabel, emptyList(), emptyList(), emptyList())
        val jumpBack = Jump(loopInstr)

        val stackToSave = loopStart.inputStack

        val loopNode = ReturnNode(Builder())
        loopNode.printer.append(loopInstr)
        loopNode.printer.append(Unreachable)
        loopNode.inputStack = emptyList()
        loopNode.outputStack = emptyList()

        // if stack is empty, we can skip saveStackNode
        val saveStackNode = if (stackToSave.isNotEmpty()) {
            createSaveStateNode(loopNode, stackToSave, mt)
        } else loopNode // skip that extra node, if not needed

        sa.nodes.add(loopNode)
        if (saveStackNode != loopNode) {
            sa.nodes.add(saveStackNode)
        }

        // ensure node.index = actual index
        renumber(sa.nodes)

        for (input in loopStart.inputs.toList()) {
            when (input) {
                loopStart -> assertFail()
                !in inLoopNodes -> {
                    // replace outputs from loopNode to saveStackNode
                    when (input) {
                        is BranchNode -> {
                            if (input.ifTrue == loopStart) input.ifTrue = saveStackNode
                            if (input.ifFalse == loopStart) input.ifFalse = saveStackNode
                        }
                        is SequenceNode -> {
                            if (input.next == loopStart) input.next = saveStackNode
                        }
                        else -> assertFail()
                    }
                    saveStackNode.inputs.add(input)
                    if (print) printState(sa.nodes, "After input-next $input")
                }
                else -> {
                    // replace outputs from loopNode to saveStackImpl + loadStackNode
                    replaceGotoEndNode(
                        sa, input, input.index, validate, print,
                        setOf(loopStart)
                    ) { node, next ->
                        assertSame(node, input)
                        assertSame(next, loopStart)
                        val builder = Builder()
                        storeStack(stackToSave, builder, mt)
                        builder.append(jumpBack)
                        builder
                    }
                }
            }
        }
        // treeNodes is invalid after this point, because nodes in it can be replaced

        loopStart.inputs.clear()

        if (print) printState(sa.nodes, "After Link Replacement")

        // solve tree
        val treeNodesList = sa.nodes.subList(startNodesSize, startNodesSize + inLoopNodes.size)
        if (validate) assertSame(loopStart, treeNodesList.first())
        val loopContent = solve(mt, treeNodesList)
        val loadStackTmp = Builder()
        loadStack(stackToSave, loadStackTmp, mt)
        loopContent.prepend(loadStackTmp)
        loopInstr.body = loopContent.instrs
        treeNodesList.clear() // remove tree from the graph

        if (print) printState(sa.nodes, "After Solving Tree")
        if (validate) validateStack(sa.nodes, sa.methodTranslator)
        validateInputOutputStacks(sa.nodes, sa.sig)
        validateInputs(sa.nodes)
    }

    private fun createSaveStateNode(
        loopNode: GraphingNode, stackToSave: List<String>,
        mt: MethodTranslator,
    ): GraphingNode {
        val saveStackNode = SequenceNode(Builder(), loopNode)
        storeStack(stackToSave, saveStackNode.printer, mt)
        saveStackNode.inputStack = stackToSave
        saveStackNode.outputStack = emptyList()
        loopNode.inputs.add(saveStackNode)
        return saveStackNode
    }
}