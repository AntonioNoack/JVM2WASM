package graphing

import graphing.LoadStoreStack.loadStackPrepend
import graphing.LoadStoreStack.storeStackAppend
import graphing.StructuralAnalysis.Companion.printState
import graphing.StructuralAnalysis.Companion.renumber
import me.anno.utils.assertions.*
import me.anno.utils.structures.lists.Lists.createArrayList
import me.anno.utils.structures.lists.Lists.sortByTopology
import me.anno.utils.structures.lists.Lists.swap
import translator.LocalVariableOrParam
import translator.MethodTranslator
import translator.MethodTranslator.Companion.comments
import utils.Builder
import utils.WASMType
import utils.WASMTypes.i32
import wasm.instr.Comment
import wasm.instr.Const
import wasm.instr.Const.Companion.i32Const1
import wasm.instr.Drop
import wasm.instr.IfBranch
import wasm.instr.Instruction.Companion.emptyArrayList
import wasm.instr.Instructions.I32EQZ
import wasm.instr.Instructions.I32Or
import wasm.instr.Instructions.Unreachable

object SolveLinearTree {

    val validate = true

    private val unusedLabel = LocalVariableOrParam("I", WASMType.I32, "lUnused", -1, false)

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
                    labels.add(mt.variables.addPrefixedLocalVariable("nF", WASMType.I32, "I"))
                    labels.add(mt.variables.addPrefixedLocalVariable("nT", WASMType.I32, "I"))
                }
                is SequenceNode -> {
                    val variable =
                        mt.variables.addPrefixedLocalVariable("n", WASMType.I32, "I")
                    labels.add(variable)
                    labels.add(variable)
                }
                else -> assertFail()
            }
            assertEquals((i + 1) * 2, labels.size)
        }
        return labels
    }

    private fun setLabelsInNodes(nodes: List<GraphingNode>, labels: List<LocalVariableOrParam>) {
        for (i in nodes.indices) {
            when (val node = nodes[i]) {
                is ReturnNode -> {}
                is BranchNode -> {
                    // set labels to result
                    val ifTrue = labels[2 * i + 1]
                    val ifFalse = labels[2 * i]
                    node.printer
                        .append(ifTrue.setter)
                        .append(ifTrue.getter).append(I32EQZ).append(ifFalse.setter)
                }
                is SequenceNode -> {
                    // set labels to true
                    node.printer
                        .append(i32Const1).append(labels[2 * i].setter)
                }
                else -> assertFail()
            }
        }
    }

    private fun unifyInputAndOutputStacks(
        nodes: List<GraphingNode>, mt: MethodTranslator,
        firstNodeIsEntry: Boolean
    ) {
        for (i in nodes.indices) {
            val node = nodes[i]
            val shallLoadStack = !(i == 0 && firstNodeIsEntry)
            // keep stack in order
            if (shallLoadStack) {
                loadStackPrepend(node, mt)
                node.inputStack = emptyList()
            } else if (comments) {
                node.printer.prepend(Comment("loading stack skipped on entry-node"))
            }
            when (node) {
                is BranchNode,
                is SequenceNode -> storeStackAppend(node, mt)
                is ReturnNode -> {}
                else -> assertFail()
            }
            node.outputStack = emptyList()
        }
    }

    private fun setLabelsTo01InNodes(nodes: List<GraphingNode>, labels: List<LocalVariableOrParam>, printer: Builder) {
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

        assertEquals(firstNodeIsEntry, extraInputs.isEmpty())

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

        val print = mt.isLookingAtSpecial
        if (print) println("SolveLinearTree[${mt.sig}, $firstNodeIsEntry, $extraInputs]")

        if (firstNodeIsEntry) {
            assertSame(nodes.first(), firstNode)
        }

        // ensure continuous IDs
        if (print) println("Sorting: ${nodes.map { it.index }}")
        renumber(nodes)

        if (validate) {
            if (print) printState(nodes, "Validating")
            StackValidator.validateStack(nodes, mt)
        }

        // create labels for all nodes
        val labels = createLabels(nodes, mt)
        val resultInputStack = if (firstNodeIsEntry) firstNode.inputStack else emptyList()
        val resultNode = createReturnNode(Builder(), resultInputStack)
        val resultPrinter = resultNode.printer

        // set labels to 1/0 at the end of each node
        setLabelsInNodes(nodes, labels)

        // make all input and output stacks [], except for first node if it's THE entry node
        unifyInputAndOutputStacks(nodes, mt, firstNodeIsEntry)

        // set all labels to 0 at the start
        setLabelsTo01InNodes(nodes, labels, resultPrinter)

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

        val addExtraUnreachable = false
        for (i in nodes.indices) {
            val node = nodes[i]
            assertEquals(i, node.index)

            val isSpecialLastNode = !addExtraUnreachable && i == nodes.lastIndex
            val extraInputs1 = extraInputs[node] ?: emptyList()
            val isMainEntryNode = firstNodeIsEntry && i == 0
            val hasRootInput = isMainEntryNode || extraInputs1.isNotEmpty()
            assertTrue(hasRootInput || node.inputs.isNotEmpty())

            val nodeInputs = node.inputs.toList()
            val printerNode = if (hasRootInput || isSpecialLastNode) {
                depth[node.index] = 0
                // println("appending ${node.index} onto root")
                resultNode
            } else {
                assertTrue(nodeInputs.isNotEmpty())
                val common = findCommonNode(nodeInputs)
                // println("appending ${node.index} onto ${common?.index ?: "root!"}")
                if (common != null) {
                    depth[node.index] = depth[common.index] + 1 // we're one deeper
                    common
                } else {
                    depth[node.index] = 0
                    resultNode
                }
            }

            val printer = printerNode.printer
            if (!isSpecialLastNode) {
                var hadCondition = false
                fun addCondition(condition: LocalVariableOrParam) {
                    printer.append(condition.getter)
                    if (hadCondition) printer.append(I32Or)
                    hadCondition = true
                }

                // sort them to make it look a little nicer
                val nodeLabels = nodeInputs.map { nodeI ->
                    val isTrue = when {
                        nodeI is BranchNode && node == nodeI.ifTrue -> 1
                        nodeI is BranchNode && node == nodeI.ifFalse -> 0
                        nodeI is SequenceNode && node == nodeI.next -> 0
                        else -> assertFail("Unknown case")
                    }
                    labels[nodeI.index * 2 + isTrue]
                }.sortedByDescending { it.index }
                for (label in nodeLabels) {
                    addCondition(label)
                }

                for (j in extraInputs1.indices) {
                    addCondition(extraInputs1[j])
                }

                // we need to create extra if-branches, because if we don't:
                //  - this is added
                //  - child decides to add content to THIS' printer (but won't be used anymore)
                //  -> code goes missing
                if (!hadCondition) printer.append(i32Const1)
                printer.append(
                    IfBranch(
                        node.printer.instrs,
                        if (node.inputStack.isNotEmpty()) {
                            createArrayList(node.inputStack.size) { Drop }
                        } else emptyArrayList,
                        node.inputStack, node.outputStack
                    )
                )

            } else {
                // the last branch is trivial :3
                printer.append(node.printer)
            }

            if (validate) {
                assertTrue(printerNode.outputStack.isEmpty())
                StackValidator.validateStack2( // O(n²)
                    mt.sig, printer, printerNode.inputStack, emptyList(), retTypes,
                    mt.variables.localVarsAndParams
                )
            }
        }

        if (addExtraUnreachable) {
            // end cannot be reached
            resultPrinter.append(Unreachable)
        } // else: we can skip the branch of the last node

        nodes.clear()
        nodes.add(resultNode)

        if (print) {
            println("\n-------------------------------------------------------------")
            println(nodes.first().printer)
        }

        StackValidator.validateNodes1(nodes, mt)

        return true
    }

    private fun createReturnNode(printer: Builder, inputStack: List<String>): ReturnNode {
        val retNode = ReturnNode(printer)
        retNode.inputStack = inputStack
        retNode.outputStack = emptyList()
        return retNode
    }
}