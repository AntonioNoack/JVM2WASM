package graphing

import graphing.LargeSwitchStatement.loadStack
import graphing.LargeSwitchStatement.storeStack
import graphing.StructuralAnalysis.Companion.printState
import graphing.StructuralAnalysis.Companion.renumber
import me.anno.utils.assertions.*
import me.anno.utils.structures.lists.Lists.sortByTopology
import me.anno.utils.structures.lists.Lists.swap
import translator.LocalVariableOrParam
import translator.MethodTranslator
import utils.Builder
import utils.WASMTypes.i32
import wasm.instr.Const
import wasm.instr.Const.Companion.i32Const1
import wasm.instr.IfBranch
import wasm.instr.Instructions.I32EQZ
import wasm.instr.Instructions.I32Or
import wasm.instr.Instructions.Unreachable

object SolveLinearTree {

    private val unusedLabel = LocalVariableOrParam("I", i32, "lUnused", -1, false)

    private fun createLabels(nodes: List<GraphingNode>, mt: MethodTranslator): List<LocalVariableOrParam> {
        val labels = ArrayList<LocalVariableOrParam>(nodes.size * 2)
        for (i in nodes.indices) {
            val node = nodes[i]
            when (node) {
                is ReturnNode -> {
                    labels.add(unusedLabel)
                    labels.add(unusedLabel)
                }
                is BranchNode -> {
                    val k = mt.linearTreeNodeIndex++
                    labels.add(mt.variables.addLocalVariable("n${k}F", i32, "I"))
                    labels.add(mt.variables.addLocalVariable("n${k}T", i32, "I"))
                }
                is SequenceNode -> {
                    val k = mt.linearTreeNodeIndex++
                    val variable = mt.variables.addLocalVariable("n${k}", i32, "I")
                    labels.add(variable)
                    labels.add(variable)
                }
                else -> assertFail()
            }
            assertEquals((i + 1) * 2, labels.size)
        }
        return labels
    }

    private fun setLabelsInNodes(nodes: List<GraphingNode>, mt: MethodTranslator, labels: List<LocalVariableOrParam>) {
        for (i in nodes.indices) {
            val node = nodes[i]
            setLabelInNode(node, i, mt, labels)
        }
    }

    private fun setLabelInNode(node: GraphingNode, i: Int, mt: MethodTranslator, labels: List<LocalVariableOrParam>) {
        // keep stack in order
        loadStack(node, mt)
        when (node) {
            is ReturnNode -> {}
            is BranchNode -> {
                // set labels to result
                val ifTrue = labels[2 * i + 1]
                val ifFalse = labels[2 * i]
                node.printer
                    .append(ifTrue.setter)
                    .append(ifTrue.getter).append(I32EQZ).append(ifFalse.setter)
                storeStack(node, mt)
            }
            is SequenceNode -> {
                // set labels to true
                node.printer
                    .append(i32Const1).append(labels[2 * i].setter)
                storeStack(node, mt)
            }
            else -> assertFail()
        }
    }

    private fun initializeLabels(nodes: List<GraphingNode>, labels: List<LocalVariableOrParam>, printer: Builder) {
        for (i in nodes.indices) {
            val node = nodes[i]
            when (node) {
                is ReturnNode -> {}
                is BranchNode -> {
                    // two labels needed
                    printer
                        .append(Const.i32Const0)
                        .append(labels[i * 2].setter)
                        .append(Const.i32Const0)
                        .append(labels[i * 2 + 1].setter)
                }
                is SequenceNode -> {
                    // just one label needed
                    printer
                        .append(Const.i32Const0)
                        .append(labels[i * 2].setter)
                }
                else -> assertFail()
            }
        }
    }

    fun trySolveLinearTree(
        nodes: MutableList<GraphingNode>, mt: MethodTranslator,
        firstNodeIsEntry: Boolean, extraInputs: Map<GraphingNode, List<LocalVariableOrParam>>
    ): Boolean {
        val retTypes = StackValidator.getReturnTypes(mt.sig)
        val firstNode = nodes.first()
        val isSorted = nodes.sortByTopology { node -> node.inputs } != null
        if (!isSorted) {
            // ensure first node is first again
            if (firstNodeIsEntry) {
                nodes.swap(0, nodes.indexOf(firstNode))
            }
            return false
        }

        val print = mt.clazz == "jvm/JVM32" && mt.name == "getInstanceSize"
        if (print) println("SolveLinearTree[${mt.sig}, $firstNodeIsEntry, $extraInputs]")

        if (firstNodeIsEntry) {
            assertSame(nodes.first(), firstNode)
        }

        // ensure continuous IDs
        if (print) println("Sorting: ${nodes.map { it.index }}")
        renumber(nodes)

        val validate = true
        if (validate) {
            if (print) {
                println(mt.sig)
                printState(nodes, "Validating")
            }
            StackValidator.validateStack(nodes, mt)
        }

        // create labels for all nodes
        val labels = createLabels(nodes, mt)
        val resultPrinter = Builder()

        // set labels to 1/0 at the end of each node
        setLabelsInNodes(nodes, mt, labels)

        // set all labels to 0 at the start
        initializeLabels(nodes, labels, resultPrinter)

        val depth = IntArray(nodes.size)
        depth.fill(-1, if (firstNodeIsEntry) 1 else 0)

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

        if (print) printState(nodes, "solveLinearTree")

        val nodesSet = nodes.toSet()
        fun findCommonNode(nodes1: List<GraphingNode>): GraphingNode? {
            assertTrue(nodes1.all { it in nodesSet })
            assertTrue(nodes1.all { it.index in nodes.indices }) { "${nodes.map { it.index }}|${nodes1.map { it.index }} vs ${nodes.size}" }
            // println("findCommonNode(${nodes1.map { it.index }})")
            val minDepth = nodes1.minOf { depth[it.index] }
            var nodes2 = nodes1.toHashSet()
            var predecessors = HashSet<GraphingNode>()
            for (depthI in minDepth downTo 0) {
                for (node in nodes2) {
                    getPredecessorsAtDepth(node, depthI, predecessors)
                }
                assertTrue(predecessors.isNotEmpty())
                // println("[${nodes2.map { it.index }},$depthI] -> ${predecessors.map { it.index }}")
                if (predecessors.size == 1) {
                    return predecessors.first()
                }
                nodes2.clear()
                val tmp = nodes2
                nodes2 = predecessors
                predecessors = tmp
            }
            if (firstNodeIsEntry) assertFail("no valid node was found")
            return null
        }

        for (i in nodes.indices) {
            val node = nodes[i]
            assertEquals(i, node.index)

            val extraInputs1 = extraInputs[node] ?: emptyList()
            val hasRootInput = (firstNodeIsEntry && i == 0) || extraInputs1.isNotEmpty()
            val hasInputs = hasRootInput || node.inputs.isNotEmpty()
            assertTrue(hasInputs)

            val nodeInputs = node.inputs.toList()
            val printer = if (hasRootInput) {
                depth[node.index] = 0
                // println("appending ${node.index} onto root")
                resultPrinter
            } else {
                assertTrue(nodeInputs.isNotEmpty())
                val common = findCommonNode(nodeInputs)
                // println("appending ${node.index} onto ${common?.index ?: "root!"}")
                if (common != null) {
                    depth[node.index] = depth[common.index] + 1 // we're one deeper
                    common.printer
                } else {
                    depth[node.index] = 0
                    resultPrinter
                }
            }

            var hadCondition = false
            fun addCondition(condition: LocalVariableOrParam) {
                printer.append(condition.getter)
                if (hadCondition) printer.append(I32Or)
                hadCondition = true
            }

            for (j in nodeInputs.indices) {
                val nodeI = nodeInputs[j]
                val isTrue = when {
                    nodeI is BranchNode && node == nodeI.ifTrue -> 1
                    nodeI is BranchNode && node == nodeI.ifFalse -> 0
                    nodeI is SequenceNode && node == nodeI.next -> 0
                    else -> assertFail("Unknown case")
                }
                addCondition(labels[nodeI.index * 2 + isTrue])
            }

            for (j in extraInputs1.indices) {
                addCondition(extraInputs1[j])
            }

            if (!hadCondition) printer.append(i32Const1)
            printer.append(IfBranch(node.printer.instrs))

            if (validate) StackValidator.validateStack2( // O(n²)
                mt.sig, printer, emptyList(), emptyList(), retTypes,
                mt.variables.localVarsWithParams
            )
        }

        // end cannot be reached; in theory, we can skip the branch of the last node
        resultPrinter.append(Unreachable)

        nodes.clear()
        nodes.add(createReturnNode(resultPrinter))

        if (print) {
            println()
            println("-------------------------------------------------------------")
            println(mt.sig)
            println()
            println(nodes.first().printer)
        }
        StackValidator.validateStack(nodes, mt)

        return true
    }

    private fun createReturnNode(printer: Builder): ReturnNode {
        val retNode = ReturnNode(printer)
        retNode.inputStack = emptyList()
        retNode.outputStack = emptyList()
        return retNode
    }
}