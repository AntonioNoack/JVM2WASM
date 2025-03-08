package graphing

import crashOnAllExceptions
import graphing.ExtractBigLoop.tryExtractBigLoop
import graphing.ExtractEndNodes.tryExtractEnd
import graphing.ExtractStartNodes.tryExtractStart
import graphing.LargeSwitchStatement.createLargeSwitchStatement2
import graphing.SolveLinearTree.trySolveLinearTree
import graphing.StackValidator.validateInputOutputStacks
import graphing.StructuralAnalysis.Companion.printOps
import me.anno.utils.assertions.*
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.utils.structures.lists.Lists.count2
import me.anno.utils.structures.lists.Lists.none2
import org.apache.logging.log4j.LogManager
import translator.MethodTranslator
import utils.Builder
import utils.i32
import wasm.instr.*
import wasm.instr.Instructions.I32EQ
import wasm.instr.Instructions.I32EQZ
import wasm.instr.Instructions.I32GES
import wasm.instr.Instructions.I32GTS
import wasm.instr.Instructions.I32LES
import wasm.instr.Instructions.I32LTS
import wasm.instr.Instructions.I32NE
import wasm.instr.Instructions.I64EQ
import wasm.instr.Instructions.I64GES
import wasm.instr.Instructions.I64GTS
import wasm.instr.Instructions.I64LES
import wasm.instr.Instructions.I64LTS
import wasm.instr.Instructions.I64NE
import wasm.instr.Instructions.Unreachable
import java.io.File

class StructuralAnalysis(
    val methodTranslator: MethodTranslator,
    val nodes: MutableList<GraphingNode>
) {

    companion object {

        private val LOGGER = LogManager.getLogger(StructuralAnalysis::class)
        val folder = File(System.getProperty("user.home"), "Desktop/Graphs")

        init {
            folder.mkdir()
        }

        var comments = true
        var printOps = false

        private val equalPairs0 = listOf(
            I32EQ to I32NE,
            I32LTS to I32GES,
            I32LES to I32GTS,

            I64EQ to I64NE,
            I64LTS to I64GES,
            I64LES to I64GTS,
        )

        val equalPairs = equalPairs0.associate { it } +
                equalPairs0.associate { it.second to it.first }

        fun renumber(nodes: List<GraphingNode>, offset: Int = 0) {
            if (printOps) println("renumbering:")
            for (j in nodes.indices) {
                val node = nodes[j]
                val newIndex = j + offset
                // if (node.hasNoCode()) throw IllegalStateException()
                if (printOps && node.index >= 0) {
                    println("${node.index} -> $newIndex")
                }
                node.index = newIndex
            }
        }

        fun renumber2(nodes: MutableList<GraphingNode>) {
            // print renumbering???
            for (i in nodes.indices) {
                nodes[i].index = -1
            }

            val currentNodes = LinkedHashSet<GraphingNode>()
            val nextNodes = ArrayList<GraphingNode>()

            var ni = 0
            nextNodes.add(nodes.first())
            while (nextNodes.isNotEmpty()) {
                val node = nextNodes.removeLast()
                if (node.index == -1) {
                    node.index = ni++
                    for (next in node.outputs) {
                        if (next.index == -1) {
                            currentNodes.add(next)
                        }
                    }
                }
                if (nextNodes.isEmpty()) {
                    nextNodes.addAll(currentNodes)
                    nextNodes.reverse()
                    currentNodes.clear()
                }
            }

            nodes.removeIf { it.index < 0 } // not reachable
            assertEquals(0, nodes[0].index) // first node must stay the same
            nodes.sortBy { it.index }
            assertEquals(0, nodes[0].index) // first node must stay the same
        }

        fun printState(nodes: List<GraphingNode>, title: String) {
            println()
            println(title)
            printState(nodes, ::println)
        }

        fun printState(nodes: List<GraphingNode>, printLine: (String) -> Unit) {
            for (i in nodes.indices) {
                val node = nodes[i]
                printLine(
                    "${"$node,".padEnd(15)}#${
                        node.inputs.map { it.index }.sorted()
                    }, ${node.inputStack} -> ${node.outputStack}, ${
                        node.printer.instrs
                            .joinToString("|")
                            .replace('\n', '|')
                    }"
                )
            }
        }

    }

    val sig get() = methodTranslator.sig

    private fun checkState(node: GraphingNode, nextNode: GraphingNode, nodeSet: Set<GraphingNode>) {
        if (node !in nextNode.inputs) {
            printState(nodes, "Invalid inputs:")
            throw IllegalStateException("Invalid inputs: ${node.index} -> ${nextNode.index}; $sig")
        } else if (nextNode !in nodeSet) {
            printState(nodes, "Missing node:")
            throw IllegalStateException("Missing node: ${node.index} -> ${nextNode.index}; $sig")
        }
    }

    private fun checkState() {
        validateInputOutputStacks(nodes, sig)
        val nodeSet = nodes.toHashSet()
        for (i in nodes.indices) {
            // check all inputs are present
            val node = nodes[i]
            when (node) {
                is BranchNode -> {
                    checkState(node, node.ifTrue, nodeSet)
                    checkState(node, node.ifFalse, nodeSet)
                }
                is SequenceNode -> {
                    checkState(node, node.next, nodeSet)
                }
            }
            // check not too many inputs are present
            for (input in node.inputs) {
                if (node !in input.outputs) {
                    printState(
                        nodes, "${node.index} is no longer returned by $input, " +
                                "only ${input.outputs.map { it.index }}"
                    )
                    throw IllegalStateException("$sig")
                } else if (input !in nodeSet) {
                    printState(nodes, "${input.index} by ${node.index}.inputs is no longer valid")
                    throw IllegalStateException("$sig")
                }
            }
        }
    }

    private fun removeUselessBranches() {
        for (i in nodes.indices) {
            val node = nodes.getOrNull(i) ?: break
            val branch = node as? BranchNode ?: continue
            removeUselessBranches(branch, i)
        }
    }

    private fun removeUselessBranches(branch: BranchNode, i: Int) {
        val lastInstr = branch.printer.lastOrNull()
        if (lastInstr is Const && lastInstr.type == ConstType.I32) {
            branch.printer.removeLast() // remove constant
            val nextNode = if (lastInstr.value != 0) branch.ifTrue else branch.ifFalse
            val ignoredNode = if (lastInstr.value != 0) branch.ifFalse else branch.ifTrue
            if (nextNode != ignoredNode) {
                assertTrue(ignoredNode.inputs.remove(branch))
            }
            val newBranch = replaceNode(branch, SequenceNode(branch.printer, nextNode), i)
            if (nextNode != ignoredNode) { // weird that this is needed...
                ignoredNode.inputs.remove(newBranch)
            }
            if (ignoredNode.index > 0 &&
                ignoredNode.index > newBranch.index && // ensure we don't skip nodes in the loop around this
                ignoredNode.inputs.isEmpty()
            ) {
                nodes.remove(ignoredNode)
            }
            if (printOps) {
                printState(nodes, "Useless branch [${newBranch.index} -> ${nextNode.index} : ${ignoredNode.index}]")
            }
            checkState()
        }
    }

    fun <V : GraphingNode> replaceNode(oldNode: GraphingNode, newNode: V, i: Int): V {
        assertNotSame(oldNode, newNode)
        assertSame(oldNode, nodes[i])
        newNode.inputs.addAll(oldNode.inputs)
        newNode.inputStack = oldNode.inputStack
        newNode.outputStack = oldNode.outputStack
        newNode.index = oldNode.index
        oldNode.index = -1
        replaceLinks(oldNode, newNode)
        nodes[i] = newNode
        return newNode
    }

    fun replaceSelfIO(oldNode: GraphingNode, newNode: GraphingNode) {
        if (oldNode in oldNode.inputs) {
            newNode.inputs.remove(oldNode)
            newNode.inputs.add(newNode)
        }
    }

    fun replaceOutputs(oldNode: GraphingNode, newNode: GraphingNode) {
        for (output in oldNode.outputs) {
            val relatedNodes = output.inputs
            relatedNodes.remove(oldNode)
            relatedNodes.add(newNode)
        }
    }

    fun replaceInputs(oldNode: GraphingNode, newNode: GraphingNode) {
        for (input in oldNode.inputs) {
            when (input) {
                is BranchNode -> {
                    input.ifTrue = replaceNodeRef(input.ifTrue, oldNode, newNode)
                    input.ifFalse = replaceNodeRef(input.ifFalse, oldNode, newNode)
                }
                is SequenceNode -> {
                    input.next = replaceNodeRef(input.next, oldNode, newNode)
                }
            }
        }
    }

    fun replaceLinks(oldNode: GraphingNode, newNode: GraphingNode) {
        replaceSelfIO(oldNode, newNode)
        replaceOutputs(oldNode, newNode)
        replaceInputs(oldNode, newNode)
    }

    private fun replaceNodeRef(isNode: GraphingNode, oldNode: GraphingNode, newNode: GraphingNode): GraphingNode {
        return if (isNode == oldNode) newNode else isNode
    }

    private fun recalculateInputs() {
        for (i in nodes.indices) {
            nodes[i].inputs.clear()
        }
        for (i in nodes.indices) {
            val node = nodes[i]
            for (next in node.outputs) {
                next.inputs.add(node)
            }
        }
    }

    private fun removeNodesWithoutInputs(): Boolean {
        val firstNode = nodes.first()
        val removed = HashSet<Int>()
        val changed = nodes.removeIf { node ->
            if (node != firstNode && node.inputs.isEmpty()) {
                removed.add(node.index)
                true
            } else false
        }
        if (changed) {
            recalculateInputs()
        }
        if (changed && printOps) {
            printState(nodes, "without inputs: $removed")
        }
        return changed
    }

    private fun removeNodesWithoutCode() {
        for (i in nodes.lastIndex downTo 0) { // reverse, so we don't trample our own feet
            val node = nodes[i]
            if (node.hasNoCode && node is SequenceNode) {
                val nextNode = node.next
                if (nextNode == node) continue

                nextNode.inputs.addAll(node.inputs)
                nextNode.inputs.remove(node)
                replaceInputs(node, nextNode)

                if (i == 0) { // always keep first node first
                    // "swap" them, then remove "node"
                    val j = nodes.indexOf(nextNode)
                    nodes[0] = nodes[j]
                    nodes.removeAt(j)
                } else {
                    nodes.removeAt(i) // remove that node
                }

                if (printOps) printState(nodes, "-${node.index}, no code")
            }
        }
    }

    private fun blockParamsGetParams(from: GraphingNode): List<String> {
        return from.inputStack
    }

    private fun blockParamsGetResult(from: GraphingNode, to: GraphingNode?): List<String> {
        if (to == null) {
            // output must be equal to input
            if (from.inputStack.isNotEmpty()) {
                return from.inputStack
            }
        } else if (from.outputStack.isNotEmpty()) {
            if (from !is ReturnNode) {
                return from.outputStack
            }
        }// else if(printOps) append(";; no return values found in ${node.index}\n")
        return emptyList()
    }

    private fun makeNodeLoop(name: String, node: GraphingNode, i: Int) {
        val label = "$name${methodTranslator.nextLoopIndex++}"
        val loopInstr = LoopInstr(label, node.printer.instrs, emptyList(), emptyList() /* cannot return anything */)
        node.printer.append(Jump(loopInstr))
        val newPrinter = Builder()
        newPrinter.append(loopInstr).append(Unreachable)
        node.inputs.remove(node) // it no longer links to itself
        replaceNode(node, ReturnNode(newPrinter), i)
    }

    private fun isEasyNode(node: GraphingNode): Boolean {
        return node.printer.instrs.count2 { it !is Comment } < 3 &&
                node.printer.instrs.none2(::isComplexInstr)
    }

    private fun isComplexInstr(it: Instruction): Boolean {
        return it is IfBranch || it is SwitchCase || it is LoopInstr
    }

    private fun joinFirstSequence(): Boolean {
        val firstNode = nodes.first()
        if (firstNode !is SequenceNode || firstNode.inputs.isNotEmpty()) {
            return false
        }

        // remove first node, and prepend it onto next node
        val next = firstNode.next
        if (next.inputs.size != 1) return false// we cannot simply prepend it

        next.printer.prepend(firstNode.printer)
        assertTrue(next.inputs.remove(firstNode))
        assertTrue(next.inputs.isEmpty())
        next.inputStack = firstNode.inputStack

        val firstNodeIndex = firstNode.index
        val nextIndex = nodes.indexOf(next)
        nodes[0] = next
        nodes.removeAt(nextIndex)
        // next.index = firstNode.index
        firstNode.index = -1

        // print update
        if (printOps) {
            printState(nodes, "Removed $firstNodeIndex via replaceSequences/first()")
        }
        checkState()

        return true
    }

    private fun joinSequences(): Boolean {
        var changed = false
        val firstNode = nodes.first()
        for (i in 1 until nodes.size) {
            val curr = nodes.getOrNull(i) ?: break
            if (curr.inputs.size == 1) {
                // merge with previous node
                val prev = curr.inputs.first()
                if (prev !is SequenceNode) continue // cannot merge
                if (prev === curr) continue // cannot merge either
                if (prev == firstNode) continue // should not remove that node;
                // replace inputs with inputs of previous node
                curr.inputs.clear()
                curr.inputs.addAll(prev.inputs)
                curr.inputStack = prev.inputStack
                // prepend the previous node to this one
                curr.printer.prepend(prev.printer)
                // replace links from prev to curr
                replaceInputs(prev, curr)
                // then delete the previous node
                nodes.remove(prev)
                changed = true

                if (printOps) {
                    printState(nodes, "Removed ${prev.index} via replaceSequences()")
                }
                checkState()
            }
        }
        return changed
    }

    private fun removeEmptyIfStatements(): Boolean {
        var changed = false
        for (i in nodes.indices) {
            val node = nodes[i] as? BranchNode ?: continue
            if (node.ifTrue != node.ifFalse) continue
            // both branches are the same -> change the branch to a sequence
            node.printer.drop().comment("ifTrue == ifFalse")
            replaceNode(node, SequenceNode(node.printer, node.ifTrue), i)
            changed = true
        }
        return changed
    }

    private fun duplicateSimpleReturnNodes(): Boolean {
        var changed = false
        for (i in nodes.lastIndex downTo 0) {
            val retNode = nodes[i]
            if (retNode.inputs.isNotEmpty() && retNode is ReturnNode && isEasyNode(retNode)) {
                for (prev in retNode.inputs.filterIsInstance2(SequenceNode::class)) {
                    prev.printer.append(retNode.printer)
                    val newPrev = replaceNode(prev, ReturnNode(prev.printer), nodes.indexOf(prev))
                    retNode.inputs.remove(newPrev)
                    if (printOps) {
                        printState(nodes, "Append ${retNode.index} onto ${newPrev.index}")
                    }
                    changed = true
                }
                if (retNode.inputs.isEmpty()) {
                    // node can be deleted
                    assertNotEquals(0, i)
                    nodes.removeAt(i)
                    if (printOps) {
                        printState(nodes, "Removed ${retNode.index}")
                    }
                }
            }
        }
        return changed
    }

    /**
     * remove dead ends by finding what is reachable
     * */
    private fun removeDeadEnds(): Boolean {
        val reachable = HashSet<GraphingNode>()
        val remaining = ArrayList<GraphingNode>()

        val firstNode = nodes.first()
        remaining.add(firstNode)
        reachable.add(firstNode)

        while (remaining.isNotEmpty()) {
            val sample = remaining.last()
            remaining.removeAt(remaining.lastIndex)
            for (b1 in sample.outputs) {
                if (reachable.add(b1)) {
                    remaining.add(b1)
                }
            }
        }

        return if (reachable.size < nodes.size) {
            val unreachable = nodes.filter { it !in reachable }
            if (!crashOnAllExceptions) {
                println("warning: found unreachable nodes:")
                for (node in unreachable) {
                    println("  $node, ${node.index}, ${node.printer.instrs}")
                }
            } // else no reason to panic, unreachable nodes are normal: all catch-clauses get discarded
            nodes.removeIf { it !in reachable }
            for (i in nodes.indices) {
                nodes[i].inputs.removeIf { it !in reachable }
            }
            if (printOps) {
                printState(nodes, "Removed unreachable nodes ${unreachable.map { it.index }}")
            }
            checkState()
            true
        } else false
    }

    private fun findWhileTrueLoops(): Boolean {
        var changed = false
        for (i in nodes.indices) {
            val node = nodes[i]
            if (node is SequenceNode && node.next == node) {
                // replace loop with wasm loop
                makeNodeLoop("whileTrue", node, i)
                // node.printer.append("  unreachable\n")
                changed = true
            }
        }
        return changed
    }

    private fun replaceWhileLoop(node: BranchNode, i: Int, negate: Boolean, nextNode: GraphingNode) {

        if (printOps) {
            printState(nodes, "Pre-WhileLoop${node.index}")
        }

        val label = "while${if (negate) "A" else "B"}${methodTranslator.nextLoopIndex++}"
        if (negate) node.printer.append(I32EQZ)
        val loopInstr = LoopInstr(label, node)
        node.printer.append(JumpIf(loopInstr))
        val newNode = replaceNode(node, SequenceNode(Builder(loopInstr), nextNode), i)
        assertTrue(newNode.inputs.remove(newNode)) // no longer recursive
        if (printOps) printState(nodes, label)
    }

    private fun findWhileLoops(): Boolean {
        // find while(x) loops
        // A -> A|B
        var changed = false
        for (i in nodes.indices) {
            val node = nodes[i] as? BranchNode ?: continue
            if (node.ifTrue == node.ifFalse) continue
            if (node == node.ifTrue) {
                renumber(nodes)
                // A ? A : B
                // loop(A, if() else break)
                replaceWhileLoop(node, i, false, node.ifFalse)
                changed = true
            } else if (node == node.ifFalse) {
                renumber(nodes)
                // A ? B : A
                // loop(A, if() break)
                replaceWhileLoop(node, i, true, node.ifTrue)
                changed = true
            } // else ignored
        }
        return changed
    }

    private fun replaceSimpleBranch(
        nodeA: BranchNode, nodeB: GraphingNode, nodeC: GraphingNode,
        negate: Boolean, i: Int
    ) {
        // we can make b0 an optional part of node
        if (negate) nodeA.printer.append(I32EQZ) // negate condition
        nodeA.printer.append(
            IfBranch(
                ArrayList(nodeB.printer.instrs), emptyList(),
                blockParamsGetParams(nodeB),
                blockParamsGetResult(nodeB, null)
            )
        )

        // node no longer is a branch
        replaceNode(nodeA, SequenceNode(nodeA.printer, nodeC), i)

        nodes.remove(nodeB)
        nodeC.inputs.remove(nodeB)

        if (printOps) {
            printState(nodes, "-${nodeB.index} by simpleBranch")
        }
    }

    /**
     * A -> B|C, B -> C
     * */
    private fun replaceSimpleBranch(): Boolean {
        var changed = false
        for (i in nodes.indices) {
            val node = nodes.getOrNull(i) ?: break
            if (node is BranchNode) {
                val b0 = node.ifFalse
                val b1 = node.ifTrue
                if (b0 is SequenceNode && b0.inputs.size == 1 && b0.next == b1) {
                    replaceSimpleBranch(node, b0, b1, true, i)
                    changed = true
                } else if (b1 is SequenceNode && b1.inputs.size == 1 && b1.next == b0) {
                    replaceSimpleBranch(node, b1, b0, false, i)
                    changed = true
                }
            }
        }
        return changed
    }

    private fun mergeGeneralBranching(): Boolean {
        // general branching
        // A -> B|C; B -> D; C -> D
        // becomes A -> D
        var changed = false
        for (i in nodes.indices) {
            val nodeA = nodes.getOrNull(i) ?: break
            if (nodeA is BranchNode) {
                val nodeB = nodeA.ifTrue
                val nodeC = nodeA.ifFalse
                if (
                    nodeB is SequenceNode && nodeC is SequenceNode &&
                    nodeB.next == nodeC.next &&
                    (nodeB.inputs.size == 1 && nodeC.inputs.size == 1) &&
                    nodeB.next != nodeA // extra-condition; we might make it work later
                ) {

                    // merge branch into nodeA
                    nodeA.printer.append(
                        IfBranch(
                            nodeB.printer.instrs, nodeC.printer.instrs,
                            blockParamsGetParams(nodeB),
                            blockParamsGetResult(nodeB, nodeC)
                        )
                    )
                    nodeA.outputStack = nodeC.outputStack

                    val nodeD = nodeB.next
                    val newNodeA = SequenceNode(nodeA.printer, nodeD)
                    replaceNode(nodeA, newNodeA, i)

                    nodeD.inputs.remove(nodeB)
                    nodeD.inputs.remove(nodeC)
                    nodeD.inputs.add(newNodeA)

                    nodes.removeAll(listOf(nodeB, nodeC))

                    if (printOps) {
                        printState(
                            nodes, "generalBranching " +
                                    "[${newNodeA.index}, ${nodeB.index}, ${nodeC.index}, ${nodeD.index}]"
                        )
                    }
                    checkState()

                    changed = true
                }
            }
        }
        return changed
    }

    private fun canReturnFirstNode(): Boolean {
        return nodes.first() is ReturnNode
    }

    private fun firstNodeForReturn(): Builder {
        assertTrue(canReturnFirstNode())
        return nodes.first().printer
    }

    /**
     * next easy step: if both branches terminate, we can join this, and mark it as ending
     * if a branch terminates, we can just add this branch without complications
     * */
    private fun findWhereBothBranchesTerminate(): Boolean {
        var changed = false
        for (i in nodes.indices) {
            val node = nodes.getOrNull(i) ?: break
            if (node is BranchNode) {
                val b0 = node.ifFalse
                val b1 = node.ifTrue

                val b0Ends = b0 is ReturnNode && b0.inputs.size == 1
                val b1Ends = b1 is ReturnNode && b1.inputs.size == 1
                if (!b0Ends && !b1Ends) continue

                if (b0Ends && b1Ends) {
                    // join exiting branches into a single exit node

                    node.printer.append(
                        IfBranch(
                            ArrayList(b1.printer.instrs), ArrayList(b0.printer.instrs),
                            blockParamsGetParams(b1),
                            blockParamsGetResult(b1, b0)
                        )
                    )
                    node.printer.append(Unreachable)

                    replaceNode(node, ReturnNode(node.printer), i)
                    nodes.removeAll(listOf(b0, b1))

                    if (printOps) {
                        printState(nodes, "-${b0.index},${b1.index} by 1/2")
                    }
                } else if (b0Ends) {
                    // merge exit into this node

                    node.printer.append(I32EQZ)
                    node.printer.append(
                        IfBranch(
                            ArrayList(b0.printer.instrs), emptyList(),
                            blockParamsGetParams(b0),
                            blockParamsGetResult(b0, null)
                        )
                    )

                    replaceNode(node, SequenceNode(node.printer, b1), i)
                    nodes.remove(b0)

                    if (printOps) {
                        printState(nodes, "-${b0.index} by 3")
                    }
                } else {
                    // merge exit into this node
                    node.printer.append(
                        IfBranch(
                            ArrayList(b1.printer.instrs), emptyList(),
                            blockParamsGetParams(b1),
                            blockParamsGetResult(b1, null)
                        )
                    )

                    replaceNode(node, SequenceNode(node.printer, b0), i)
                    nodes.remove(b1)

                    if (printOps) {
                        printState(nodes, "-${b1.index} by 4")
                    }
                }

                changed = true
            }
        }
        return changed
    }

    /**
     * small circle
     * A -> B | C; B -> A
     * */
    private fun findSmallCircleA(): Boolean {
        var changed = false
        for (i in nodes.indices) {

            val nodeA = nodes.getOrNull(i) ?: break
            if (nodeA !is BranchNode) continue

            fun isSmallCircle(nextNode: GraphingNode): Boolean {
                return nextNode is SequenceNode &&
                        nextNode.next == nodeA && nextNode.inputs.size == 1
            }

            val ifTrue = nodeA.ifTrue
            val ifFalse = nodeA.ifFalse
            if (ifTrue == ifFalse) continue // not though over

            val ifTrueIsCircle = isSmallCircle(ifTrue)
            val ifFalseIsCircle = isSmallCircle(ifFalse)

            val nodeB: GraphingNode
            val nodeC: GraphingNode
            val negate: Boolean

            if (ifTrueIsCircle && !ifFalseIsCircle) {
                nodeB = ifTrue
                nodeC = ifFalse
                negate = false
            } else if (ifFalseIsCircle && !ifTrueIsCircle) {
                nodeB = ifFalse
                nodeC = ifTrue
                negate = true
            } else continue

            changed = true

            val label = "smallCircleA${methodTranslator.nextLoopIndex++}"
            val jump = Jump(BreakableInstruction.tmp)
            nodeB.printer.append(jump)
            if (negate) nodeA.printer.append(I32EQZ)
            nodeA.printer.append(
                IfBranch(
                    nodeB.printer.instrs, emptyList(),
                    blockParamsGetParams(nodeB),
                    blockParamsGetResult(nodeB, null)
                )
            )

            val loopInstr = LoopInstr(label, nodeA)
            jump.owner = loopInstr
            nodeA.inputs.remove(nodeB)
            val newNodeA = replaceNode(nodeA, SequenceNode(Builder(loopInstr), nodeC), i)
            nodes.remove(nodeB)

            if (printOps) {
                printState(nodes, "$label, [${newNodeA.index}, ${nodeB.index}, ${nodeC.index}]")
            }
        }
        return changed
    }

    /**
     * small circle
     * A -> B | C; B -> A | C
     * */
    private fun findSmallCircleB(): Boolean {
        var changed = false
        for (i in nodes.indices) {

            val nodeA = nodes.getOrNull(i) ?: break
            if (nodeA !is BranchNode) continue

            fun isValidBAndC(nodeB: GraphingNode, nodeC: GraphingNode): Boolean {
                // check that B only goes to A and C
                return nodeB is BranchNode && (
                        (nodeB.ifTrue == nodeA && nodeB.ifFalse == nodeC) ||
                                (nodeB.ifTrue == nodeC && nodeB.ifFalse == nodeA)) &&
                        nodeB.inputs.size == 1
            }

            fun negateInner(nodeB: BranchNode, nodeC: GraphingNode): Boolean {
                return nodeB.ifTrue == nodeA && nodeB.ifFalse == nodeC
            }

            val ifTrue = nodeA.ifTrue
            val ifFalse = nodeA.ifFalse
            if (ifTrue == ifFalse) continue // not thought over yet

            val ifTrueIsB = isValidBAndC(ifTrue, ifFalse)
            val ifFalseIsB = isValidBAndC(ifFalse, ifTrue)

            val nodeB: BranchNode
            val nodeC: GraphingNode
            val negate: Boolean

            if (ifTrueIsB && !ifFalseIsB) {
                nodeB = ifTrue as BranchNode
                nodeC = ifFalse
                negate = false
            } else if (ifFalseIsB && !ifTrueIsB) {
                nodeB = ifFalse as BranchNode
                nodeC = ifTrue
                negate = true
            } else continue

            val label = "smallCircleB${methodTranslator.nextLoopIndex++}"
            if (negateInner(nodeB, nodeC)) {
                nodeB.printer.append(I32EQZ)
            }
            val jump = JumpIf(BreakableInstruction.tmp)
            nodeB.printer.append(jump)
            if (negate) nodeA.printer.append(I32EQZ)
            nodeA.printer.append(
                IfBranch(
                    nodeB.printer.instrs, emptyList(),
                    blockParamsGetParams(nodeB),
                    blockParamsGetResult(nodeB, null)
                )
            )

            val loopInstr = LoopInstr(label, nodeA)
            jump.owner = loopInstr
            nodeA.inputs.remove(nodeB)
            nodeC.inputs.remove(nodeB)
            val newNodeA = replaceNode(nodeA, SequenceNode(Builder(loopInstr), nodeC), i)
            nodes.remove(nodeB)

            if (printOps) {
                printState(nodes, "smallCircleB [${newNodeA.index}, ${nodeB.index}, ${nodeC.index}]")
            }
            checkState()

            changed = true
        }
        return changed
    }

    /**
     * merge small circles
     * A -> B; B -> A; only entry: A
     * */
    private fun mergeSmallCircles(): Boolean {
        val print = printOps && nodes.size == 2
        var changed = false
        val firstNode = nodes.first()
        for (i in nodes.indices) {
            val node1 = nodes.getOrNull(i) ?: break
            if (print) println("node1: $node1")
            if (node1 is SequenceNode) {
                val node2 = node1.next
                if (print) println("node2: $node2")
                if (node1 != node2 && node2 is SequenceNode && node2.next == node1) {
                    // we found such a circle
                    val entry1 = node1.inputs.size > 1
                    val entry2 = node2.inputs.size > 1
                    if (print) println("e1/e2: $entry1/$entry2")
                    if (entry1 && entry2) {
                        // TO DO("duplicate functionality")
                        // code duplication -> ignore this case
                        continue
                    } else if (entry1 && node2 != firstNode) {
                        // append entry2 to entry1
                        node1.printer.append(node2.printer)
                        node1.inputs.remove(node2)
                        node1.outputStack = node2.outputStack
                        nodes.remove(node2)
                        makeNodeLoop("mergeSmallCircleA", node1, i)
                        if (printOps) printState(nodes, "-${node2.index} by mergeSmallCircles/1")
                    } else if (node1 != firstNode) {
                        // append entry1 to entry2
                        node2.printer.append(node1.printer)
                        node2.inputs.remove(node1)
                        node2.outputStack = node1.outputStack
                        nodes.remove(node1)
                        val node2i = nodes.indexOf(node2)
                        makeNodeLoop("mergeSmallCircleB", node2, node2i)
                        if (printOps) printState(nodes, "-${node1.index} by mergeSmallCircles/2")
                    }
                    checkState()
                    changed = true
                }
            }
        }
        return changed
    }

    /**
     * transform all nodes into some nice if-else-tree
     * */
    fun joinNodes(): Builder {

        printOps = methodTranslator.isLookingAtSpecial

        if (printOps) {
            println("\n[SA] ${sig.clazz} ${sig.name} ${sig.descriptor}: ${nodes.size}")
        }

        assertFalse(nodes.isEmpty())
        if (canReturnFirstNode()) {
            return firstNodeForReturn()
        }

        for (node in nodes) {
            node.hasNoCode = node.printer.instrs.all { it is Comment }
        }

        recalculateInputs()
        checkState()

        if (printOps) {
            printState(nodes, "Start")
        }

        removeUselessBranches()
        checkState()

        removeNodesWithoutInputs()
        checkState()

        removeNodesWithoutCode()
        checkState()

        if (canReturnFirstNode()) {
            return firstNodeForReturn()
        }

        StackValidator.validateStack(nodes, methodTranslator)

        while (true) {
            var hadAnyChange = false
            for (step in 0 until 14) {
                while (true) {
                    val hadStepChange = when (step) {
                        0 -> removeEmptyIfStatements()
                        1 -> duplicateSimpleReturnNodes()
                        2 -> joinFirstSequence()
                        3 -> joinSequences()
                        4 -> removeDeadEnds()
                        5 -> findWhereBothBranchesTerminate()
                        6 -> findWhileTrueLoops()
                        7 -> findWhileLoops()
                        8 -> replaceSimpleBranch()
                        9 -> mergeGeneralBranching()
                        10 -> mergeSmallCircles()
                        11 -> findSmallCircleA()
                        12 -> findSmallCircleB()
                        13 -> removeNodesWithoutInputs()
                        else -> false
                    }
                    if (hadStepChange) {
                        if (printOps) println("Change by [$step]")
                        checkState()
                        if (canReturnFirstNode()) {
                            if (printOps) printState(nodes, "solved normally")
                            return firstNodeForReturn()
                        }
                        hadAnyChange = true
                    } else break
                }
            }

            if (!hadAnyChange) {
                checkState()
                if (trySolveLinearTree(nodes, methodTranslator, true, emptyMap())) {
                    assertTrue(canReturnFirstNode())
                    if (printOps) printState(nodes, "solved linear")
                    return firstNodeForReturn()
                }
            }

            if (!hadAnyChange) {
                checkState()
                if (tryExtractStart(this)) {
                    hadAnyChange = true
                }
            }

            if (!hadAnyChange) {
                checkState()
                if (tryExtractEnd(this)) {
                    assertTrue(canReturnFirstNode())
                    if (printOps) printState(nodes, "solved extract end")
                    return firstNodeForReturn()
                }
            }


            if (!hadAnyChange) {
                checkState()
                if (tryExtractBigLoop(this)) {
                    hadAnyChange = true
                }
            }

            if (!hadAnyChange) {
                hadAnyChange = removeNodesWithoutInputs() // this should be handled by the loop above!!
                if (hadAnyChange) LOGGER.warn("Secondary check was somehow successful")
            }

            if (!hadAnyChange) {
                if (printOps) printState(nodes, "Before large switch statement:")
                methodTranslator.addLocalVariable("lbl", i32, "I")
                val switchStatement = createLargeSwitchStatement2(this)
                if (printOps) printState(nodes, "solved large switch statement")
                return switchStatement
            }
        }

        // to do solve this problem
        // possible ways:
        // - implement Dream from "No More Gotos: Decompilation Using Pattern-Independent Control-Flow Structuring and Semantics-Preserving Transformations"
        //      this apparently is implemented in https://github.com/fay59/fcd
        //      it's discussed at https://stackoverflow.com/questions/27160506/decompiler-how-to-structure-loops

        // - implement sub-functions that jump to each other
        // - implement a large switch-case statement, that loads the stack, stores the stack, and sets the target label for each remaining round 😅

    }
}

