package graphing

import graphing.LargeSwitchStatement.loadStack
import graphing.LargeSwitchStatement.storeStack
import graphing.StructuralAnalysis.Companion.printState
import graphing.StructuralAnalysis.Companion.renumber
import me.anno.utils.assertions.*
import me.anno.utils.structures.lists.Lists.sortByTopology
import me.anno.utils.structures.lists.Lists.swap
import me.anno.utils.types.Booleans.toInt
import translator.LocalVar
import utils.i32
import wasm.instr.Const
import wasm.instr.IfBranch
import wasm.instr.Instructions.I32EQZ
import wasm.instr.Instructions.I32Or
import wasm.instr.Instructions.Unreachable
import wasm.parser.LocalVariable

object SolveLinearTree {

    private val unusedLabel = LocalVar("I", i32, "lUnused", -1, false)

    fun solve(nodes: MutableList<GraphingNode>, sa: StructuralAnalysis): Boolean {

        val retTypes = StackValidator.getReturnTypes(sa.sig)
        val firstNode = nodes.first()
        val isSorted = nodes.sortByTopology { node -> node.inputs } != null
        if (!isSorted) {
            // ensure first node is first again
            nodes.swap(0, nodes.indexOf(firstNode))
            return false
        }

        val print = false
        if (print) println("SolveLinearTree[${sa.sig}]")

        assertSame(nodes.first(), firstNode)
        renumber(nodes)

        val validate = true
        if (validate) {
            if (print) {
                println(sa.sig)
                printState(sa.nodes, "Validating")
            }
            StackValidator.validateStack(nodes, sa.methodTranslator)
        }

        // create labels for all nodes
        val labels = ArrayList<LocalVar>(nodes.size * 2)
        for (i in nodes.indices) {
            val node = nodes[i]
            if (node.isReturn) {
                labels.add(unusedLabel)
                labels.add(unusedLabel)
                continue
            }

            if (node.isBranch) {
                labels.add(LocalVar("I", i32, "n${i}F", -1 - i, false))
                labels.add(LocalVar("I", i32, "n${i}T", -1 - i, false))
            } else {
                val variable = LocalVar("I", i32, "n${i}", -1 - i, false)
                labels.add(variable)
                labels.add(variable)
            }
            for (j in 0 until node.isBranch.toInt(2, 1)) {
                val label = labels[i * 2 + j]
                sa.methodTranslator.localVariables1
                    .add(LocalVariable(label.wasmName, label.wasmType))
                sa.methodTranslator.localVarsWithParams
                    .add(LocalVar("I", label.wasmType, label.wasmName, -1, false))
            }
        }

        // set labels to 1/0 at the end of each node
        for (i in nodes.indices) {
            val node = nodes[i]
            // keep stack in order
            loadStack(node, sa)
            if (node.isReturn) continue
            if (node.isBranch) {
                // set labels to result
                val ifTrue = labels[2 * i + 1]
                val ifFalse = labels[2 * i]
                node.printer
                    .append(ifTrue.localSet)
                    .append(ifTrue.localGet).append(I32EQZ).append(ifFalse.localSet)
            } else {
                // set labels to true
                node.printer
                    .append(Const.i32Const1).append(labels[2 * i].localSet)
            }
            storeStack(node, sa)
        }

        // set all labels to 0 at the start
        for (i in 1 until nodes.size) {
            val node = nodes[i]
            if (node.isReturn) continue
            if (node.isBranch) {
                // two labels needed
                firstNode.printer
                    .append(Const.i32Const0)
                    .append(labels[i * 2].localSet)
                    .append(Const.i32Const0)
                    .append(labels[i * 2 + 1].localSet)
            } else {
                // just one label needed
                firstNode.printer
                    .append(Const.i32Const0)
                    .append(labels[i * 2].localSet)
            }
        }

        val depth = IntArray(nodes.size)
        depth.fill(-1, 1)

        fun getPredecessorsAtDepth(node: GraphingNode, targetDepth: Int, dst: HashSet<GraphingNode>) {
            val depthI = depth[node.index]
            assertNotEquals(-1, depthI)
            if (depthI > targetDepth) {
                for (input in node.inputs) {
                    getPredecessorsAtDepth(input, targetDepth, dst)
                }
            } else {
                dst.add(node)
            }
        }

        fun findCommonNode(nodes1: List<GraphingNode>): GraphingNode {
            assertTrue(nodes1.all { it.index in nodes.indices }) { "${nodes.map { it.index }}|${nodes1.map { it.index }} vs ${nodes.size}" }
            val minDepth = nodes1.minOf { depth[it.index] }
            var nodes2 = nodes1.toHashSet()
            var predecessors = HashSet<GraphingNode>()
            for (depthI in minDepth downTo 0) {
                for (node in nodes2) {
                    getPredecessorsAtDepth(node, depthI, predecessors)
                }
                assertTrue(predecessors.isNotEmpty())
                if (predecessors.size == 1) {
                    return predecessors.first()
                }
                nodes2.clear()
                val tmp = nodes2
                nodes2 = predecessors
                predecessors = tmp
            }
            assertFail("no valid node was found")
        }

        val skipLastCondition = false
        for (i in 1 until nodes.size) {
            val node = nodes[i]
            assertEquals(i, node.index)
            if (skipLastCondition && i == nodes.lastIndex) {
                // last node has no other choice, and shouldn't be in any sub-branch
                firstNode.printer.append(node.printer)
            } else {
                val nodeInputs = node.inputs.toList()
                assertTrue(nodeInputs.isNotEmpty())
                val common = findCommonNode(nodeInputs)
                depth[node.index] = depth[common.index] + 1 // we're one deeper

                // add the code to common node
                // add the condition here
                for (j in nodeInputs.indices) {
                    val nodeI = nodeInputs[j]
                    val isTrue = when {
                        nodeI is BranchNode && node == nodeI.ifTrue -> 1
                        nodeI is BranchNode && node == nodeI.ifFalse -> 0
                        nodeI is SequenceNode && node == nodeI.next -> 0
                        else -> assertFail("Unknown case")
                    }
                    val condition = labels[nodeI.index * 2 + isTrue]
                    common.printer.append(condition.localGet)
                    if (j > 0) common.printer.append(I32Or)
                }

                common.printer.append(IfBranch(node.printer.instrs))
                if (validate) StackValidator.validateStack2(
                    sa.sig, node.printer, emptyList(), emptyList(), retTypes,
                    sa.methodTranslator.localVarsWithParams
                )
            }
        }

        if (!skipLastCondition) {
            // end cannot be reached; in theory, we can skip the branch of the last node
            firstNode.printer.append(Unreachable)
        }

        nodes.clear()
        val retNode = ReturnNode(firstNode.printer)
        retNode.inputStack = emptyList()
        retNode.outputStack = emptyList()
        nodes.add(retNode)

        if (sa.sig.name == "getType" && sa.sig.descriptor == "(Ljava_lang_String;)Lme_anno_input_KeyCombinationXType;") {
            println()
            println("-------------------------------------------------------------")
            println(sa.sig)
            println()
            println(retNode.printer)
        }
        StackValidator.validateStack(nodes, sa.methodTranslator)

        return true
    }
}