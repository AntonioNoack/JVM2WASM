package graphing

import graphing.LoadStoreStack.loadStackPrepend
import graphing.LoadStoreStack.storeStackAppend
import graphing.StackDepthTester.findMinDepth
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
import wasm.instr.*
import wasm.instr.Const.Companion.i32Const1
import wasm.instr.Instruction.Companion.emptyArrayList
import wasm.instr.Instructions.I32EQZ
import wasm.instr.Instructions.I32Or
import wasm.instr.Instructions.Unreachable

object SolveLinearTree {

    val validate = true

    private fun createLabel(prefix: String, mt: MethodTranslator): LocalVariableOrParam {
        return mt.variables.addPrefixedLocalVariable(prefix, WASMType.I32, "boolean")
    }

    private fun createLabels(nodes: List<GraphingNode>, mt: MethodTranslator): List<LocalVariableOrParam?> {
        val labels = ArrayList<LocalVariableOrParam?>(nodes.size * 2)
        for (i in nodes.indices) {
            when (nodes[i]) {
                is ReturnNode -> {
                    labels.add(null)
                    labels.add(null)
                }
                is BranchNode -> {
                    labels.add(createLabel("nT", mt))
                    labels.add(createLabel("nF", mt))
                }
                is SequenceNode -> {
                    labels.add(createLabel("n", mt))
                    labels.add(null)
                }
                else -> assertFail()
            }
            assertEquals((i + 1) * 2, labels.size)
        }
        return labels
    }

    private fun initLabelsInNodes(nodes: List<GraphingNode>, labels: List<LocalVariableOrParam?>, printer: Builder) {
        for (i in nodes.indices) {
            val i2 = i * 2
            when (nodes[i]) {
                is ReturnNode -> {}
                is BranchNode -> {
                    printer.append(Const.i32Const0).append(labels[i2]!!.setter)
                    printer.append(Const.i32Const0).append(labels[i2 + 1]!!.setter)
                }
                is SequenceNode -> {
                    printer.append(Const.i32Const0).append(labels[i2]!!.setter)
                }
                else -> assertFail()
            }
        }
    }

    private fun setLabelsInNodes(nodes: List<GraphingNode>, labels: List<LocalVariableOrParam?>) {
        for (i in nodes.indices) {
            val i2 = i * 2
            when (val node = nodes[i]) {
                is ReturnNode -> {}
                is BranchNode -> {
                    // set label to result, and its inverse
                    node.printer
                        .append(labels[i2]!!.setter)
                        .append(labels[i2]!!.getter)
                        .append(I32EQZ)
                        .append(labels[i2 + 1]!!.setter)
                }
                is SequenceNode -> {
                    // set label to true
                    node.printer.append(i32Const1).append(labels[i2]!!.setter)
                }
                else -> assertFail()
            }
        }
    }

    private fun <V> List<V>.takeNLast(n: Int): List<V> {
        assertTrue(size >= n) { "Expected at least $size for $n used, $this" }
        return subList(size - n, size)
    }

    private fun unifyInputAndOutputStacks(
        nodes: List<GraphingNode>, mt: MethodTranslator,
        firstNodeIsEntry: Boolean
    ) {
        for (i in nodes.indices) {
            val node = nodes[i]
            val shallLoadStack = !(i == 0 && firstNodeIsEntry)
            // we can save on loading/storing the stack, if its elements aren't used:
            //  find out how much stack is being used
            val maxStackSize = -findMinDepth(mt.sig, node.printer.instrs, node.inputStack.size)
            val skippedOnStack = node.inputStack.size - maxStackSize
            // keep stack in order
            if (shallLoadStack) {
                val inputsToLoad = node.inputStack
                    .takeNLast(maxStackSize)
                loadStackPrepend(inputsToLoad, node.printer, mt, skippedOnStack)
                node.inputStack = emptyList()
            } else if (comments) {
                node.printer.prepend(Comment("loading stack skipped on entry-node"))
            }
            when (node) {
                is BranchNode, is SequenceNode -> {
                    val outputsToStore = node.outputStack
                        .takeNLast(node.outputStack.size - skippedOnStack)
                    storeStackAppend(outputsToStore, node.printer, mt, skippedOnStack)
                }
                is ReturnNode -> {}
                else -> assertFail()
            }
            node.outputStack = emptyList()
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
        initLabelsInNodes(nodes, labels, resultPrinter)

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
            var last1Label: String? = null
            if (printer.instrs.size >= 2) {
                if (printer.instrs[printer.instrs.size - 2] == i32Const1) {
                    last1Label = (printer.instrs.last() as? LocalSet)?.name
                }
            }

            if (!isSpecialLastNode) {
                var hadCondition = false
                fun addCondition(condition: LocalVariableOrParam) {
                    if (condition.name == last1Label) return
                    printer.append(condition.getter)
                    if (hadCondition) printer.append(I32Or)
                    hadCondition = true
                }

                // sort them to make it look a little nicer
                val nodeLabels = nodeInputs.map { nodeI ->
                    val isTrue = when {
                        nodeI is BranchNode && node == nodeI.ifTrue -> 0
                        nodeI is BranchNode && node == nodeI.ifFalse -> 1
                        nodeI is SequenceNode && node == nodeI.next -> 0
                        else -> assertFail("Unknown case")
                    }
                    labels[nodeI.index * 2 + isTrue]
                }.sortedByDescending { it!!.index }
                for (label in nodeLabels) {
                    addCondition(label!!)
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