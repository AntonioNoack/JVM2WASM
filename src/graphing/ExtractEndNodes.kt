package graphing

import graphing.LargeSwitchStatement.storeStack
import graphing.StackValidator.validateStack
import graphing.StructuralAnalysis.Companion.printState
import graphing.StructuralAnalysis.Companion.renumber
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFail
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.lists.Lists.all2
import me.anno.utils.structures.lists.Lists.partition1
import translator.LocalVar
import utils.Builder
import utils.i32
import wasm.instr.Const.Companion.i32Const0
import wasm.instr.Const.Companion.i32Const1
import wasm.instr.IfBranch
import wasm.instr.Instructions.I32EQZ
import wasm.instr.Instructions.Unreachable
import wasm.instr.Jump
import wasm.instr.LoopInstr

/**
 * Extract all definitely returning, non-recursive nodes.
 * Convert them into a tree, and solve the start traditionally.
 * */
object ExtractEndNodes {

    fun tryExtractEnd(sa: StructuralAnalysis): Boolean {
        val endNodes = findEndNodes(sa)
        if (shouldReplaceEndNodes(endNodes)) {
            val builder = createMergedCode(sa, endNodes)
            sa.nodes.clear()
            val endNode = ReturnNode(builder)
            endNode.inputStack = emptyList()
            endNode.outputStack = emptyList()
            sa.nodes.add(endNode)
            validateStack(sa.nodes, sa.methodTranslator)
            return true
        } else return false
    }

    private fun findEndNodes(sa: StructuralAnalysis): Set<GraphingNode> {
        // check all nodes, whose following nodes are not recursive
        val capacity = sa.nodes.size
        val endNodes = HashMap<GraphingNode, Boolean>(capacity)
        for (node in sa.nodes) {
            isEndNode(node, endNodes)
        }
        return endNodes.filter { it.value }.keys
    }

    private fun isEndNode(node: GraphingNode, endNodeCache: HashMap<GraphingNode, Boolean>): Boolean {
        val value = endNodeCache[node]
        if (value == null) {
            // if we access this node recursively, this node is not an endNode
            endNodeCache[node] = false
            // first time -> investigate it
            val answer = node.outputs.all2 { output ->
                isEndNode(output, endNodeCache)
            }
            // store what we found for later use
            endNodeCache[node] = answer
            return answer
        } else return value
    }

    private fun shouldReplaceEndNodes(endNodes: Collection<GraphingNode>): Boolean {
        return when (endNodes.size) {
            0 -> false
            1 -> endNodes.first().inputs.size > 1
            else -> true
        }
    }

    private fun validateNodes(sa: StructuralAnalysis, endNodes: Set<GraphingNode>) {
        assertTrue(endNodes.size < sa.nodes.size)
        assertTrue(sa.nodes.first() !in endNodes)
        for (node in sa.nodes) {
            if (node.outputs.all2 { it in endNodes }) {
                assertTrue(node in endNodes) {
                    "${node.index} should be endNode"
                }
            }
        }
    }

    private fun createMergedCode(sa: StructuralAnalysis, endNodes: Set<GraphingNode>): Builder {

        val print = false
        val validate = true

        if (print) {
            val firstNode = sa.nodes.first()
            sa.nodes.partition1 { it !in endNodes }
            assertEquals(firstNode, sa.nodes.first())
            renumber(sa.nodes)
        }

        if (print) {
            println()
            println("------------------------------------------------------")
            println(
                "ExtractEnd, " +
                        "nodes: 0-${sa.nodes.lastIndex - endNodes.size}, " +
                        "end: ${sa.nodes.size - endNodes.size}-${sa.nodes.lastIndex}"
            )
            printState(sa.nodes, "MergeCode")
        }

        if (validate) validateNodes(sa, endNodes)

        val mt = sa.methodTranslator
        val firstRunLabel = "firstRunE${mt.endNodeExtractorIndex++}"
        val firstRunVariable = mt.addLocalVariable(firstRunLabel, i32, "I")
        val loopInstr = LoopInstr(firstRunLabel, emptyList(), emptyList(), emptyList())
        val jumpInstr = Jump(loopInstr)

        val extraInputs = HashMap<GraphingNode, List<LocalVar>>()
        fun getInputLabel(endNode: GraphingNode): LocalVar {
            assertTrue(endNode in endNodes)
            return extraInputs.getOrPut(endNode) {
                val name = "endNode${mt.endNodeIndex++}"
                listOf(mt.addLocalVariable(name, i32, "I"))
            }.first()
        }

        if (validate) validateStack(sa.nodes, mt)

        // replace goto-end-node to jumps to it
        assertTrue(sa.nodes.all2 { it in endNodes || it !is ReturnNode })
        replaceGotoEndNodes(sa, validate, print, endNodes) { node, next ->
            val setLabel = getInputLabel(next)
            val branchPrinter = Builder()
            storeStack(branchPrinter, node.outputStack, mt)
            // append "label-setter"
            branchPrinter.append(i32Const1).append(setLabel.setter)
            // branch to loop start = the beginning of the end
            branchPrinter.append(jumpInstr)
            branchPrinter
        }
        sa.nodes.removeIf { it in endNodes }
        if (print) printState(sa.nodes, "Removed end nodes")

        val firstNodeCode = solve(sa, sa.nodes)
        firstNodeCode.prepend(listOf(i32Const0, firstRunVariable.setter)) // don't run a second time
        firstNodeCode.append(Unreachable) // end of it should be unreachable

        val endNodesList = ArrayList(endNodes)
        for (endNode in endNodesList) {
            // unlink inputs
            endNode.inputs.removeIf { it !in endNodes }
        }

        assertTrue(extraInputs.isNotEmpty())
        if (print) println(endNodesList.map { node -> "${node.index}.extra=${extraInputs[node]?.map { it.wasmName }}" })
        assertTrue(SolveLinearTree.trySolveLinearTree(endNodesList, mt, false, extraInputs))
        assertEquals(1, endNodesList.size)
        val endNodeCode = endNodesList.first().printer

        loopInstr.body = listOf(
            firstRunVariable.getter,
            IfBranch(firstNodeCode.instrs, endNodeCode.instrs, emptyList(), emptyList()),
            Unreachable // should be unreachable, too
        )

        val dst = Builder(3)
        dst.append(i32Const1).append(firstRunVariable.setter)
        dst.append(loopInstr)
        return dst
    }

    fun solve(sa: StructuralAnalysis, nodes: List<GraphingNode>): Builder {
        // good enough???
        return StructuralAnalysis(sa.methodTranslator, ArrayList(nodes))
            .joinNodes()
    }

    fun replaceGotoEndNodes(
        sa: StructuralAnalysis,
        validate: Boolean, print: Boolean,
        endNodes: Set<GraphingNode>,
        getJumpInstructions: (node: GraphingNode, next: GraphingNode) -> Builder
    ) {
        // replace goto-end-node to jumps to it
        for (i in sa.nodes.indices) {
            val node = sa.nodes[i]
            if (node in endNodes) continue

            when (node) {
                is ReturnNode -> {} // nothing to replace
                is BranchNode -> {
                    val endTrue = node.ifTrue in endNodes
                    val endFalse = node.ifFalse in endNodes
                    when {
                        endTrue && endFalse -> {
                            // create return node
                            printState(sa.nodes, "Illegal node")
                            throw IllegalStateException("Node ${node.index} should be part of endNodes")
                        }
                        endTrue || endFalse -> {
                            // printState(sa.nodes, "Before if-jump $node, $endTrue, $endFalse")
                            if (endFalse) node.printer.append(I32EQZ)
                            val endNode = if (endTrue) node.ifTrue else node.ifFalse
                            node.printer.append(
                                IfBranch(
                                    getJumpInstructions(node, endNode).instrs, emptyList(),
                                    node.outputStack, node.outputStack
                                )
                            )
                            // create sequence node
                            val next = if (endTrue) node.ifFalse else node.ifTrue
                            sa.replaceNode(node, SequenceNode(node.printer, next), i)
                            if (print) printState(sa.nodes, "After if-jump [$i]")
                            if (validate) validateStack(sa.nodes, sa.methodTranslator)
                        }
                        else -> {} // nothing to do
                    }
                }
                is SequenceNode -> {
                    if (node.next in endNodes) {
                        // convert node to return node
                        val builder = getJumpInstructions(node, node.next)
                        builder.prepend(node.printer)
                        sa.replaceNode(node, ReturnNode(builder), i)
                        if (print) printState(sa.nodes, "After next-jump [$i]")
                        if (validate) validateStack(sa.nodes, sa.methodTranslator)
                    }
                }
                else -> assertFail()
            }
        }
    }

}