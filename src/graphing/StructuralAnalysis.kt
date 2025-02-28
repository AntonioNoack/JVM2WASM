package graphing

import graphing.LargeSwitchStatement.createLargeSwitchStatement2
import me.anno.utils.assertions.assertFalse
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.lists.Lists.count2
import me.anno.utils.structures.lists.Lists.none2
import me.anno.utils.structures.tuples.IntPair
import org.objectweb.asm.Label
import translator.LocalVar
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
import wasm.instr.Instructions.Return
import wasm.instr.Instructions.Unreachable
import wasm.parser.LocalVariable
import java.io.File

class StructuralAnalysis(
    val methodTranslator: MethodTranslator,
    val nodes: MutableList<Node>
) {

    companion object {

        val folder = File(System.getProperty("user.home"), "Desktop/Graphs")

        init {
            folder.mkdir()
        }

        var comments = true

        private var prettyPrint = false
        var printOps = prettyPrint
        var ignoreSize = false
        var compactStatePrinting = true

        var lastSize = 0

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

        fun renumber(nodes: List<Node>) {
            if (printOps) println("renumbering:")
            for (j in nodes.indices) {
                val node = nodes[j]
                // if (node.hasNoCode()) throw IllegalStateException()
                if (printOps && node.index >= 0) {
                    println("${node.index} -> $j")
                }
                node.index = j
            }
        }
    }

    var loopIndex = 0

    val labelToNode = HashMap(nodes.associateBy { it.label })

    val sig get() = methodTranslator.sig

    fun printState(printLine: (String) -> Unit) {

        for (node in nodes) {
            val name = node.toString { label ->
                labelToNode[label]?.index.toString()
            }
            if (compactStatePrinting) {
                printLine(
                    "${"${name},".padEnd(15)}#${
                        node.inputs.map { it.index }.sorted()
                    }, ${node.inputStack} -> ${node.outputStack}, ${
                        node.printer.instrs
                            .joinToString("|")
                            .replace('\n', '|')
                    }"
                )
            } else {
                printLine(
                    "${"${name},".padEnd(15)}#${
                        node.inputs.map { it.index }.sorted()
                    }, ${node.inputStack} -> ${node.outputStack}"
                )
                printLine("")
                for (line in node.printer.instrs) {
                    printLine("             $line")
                }
            }
        }
    }

    private fun checkState() {
        for (node in nodes) {
            val tr = labelToNode[node.ifTrue]
            if (tr != null && tr !in nodes) {
                printState(::println)
                throw IllegalStateException("${node.index} -> ${tr.index}")
            }
            val fs = node.ifFalse
            if (fs != null && fs !in nodes) {
                printState(::println)
                throw IllegalStateException("${node.index} -> ${fs.index}")
            }
        }
    }

    private fun removeUselessBranches(node: Node) {
        if (!node.isBranch) return
        val lastNode = node.printer.lastOrNull()
        if (lastNode is Const && lastNode.type == ConstType.I32) {
            node.printer.removeLast()
            if (lastNode.value == 0) {
                // always take false path
                node.ifTrue = null
            } else {
                // always take true path
                node.ifFalse = null
                node.isAlwaysTrue = true
            }
        }
    }

    private fun recalculateInputs() {
        for (node in nodes) {
            node.inputs.clear()
        }
        for (node in nodes) {
            val tr = labelToNode[node.ifTrue]
            tr?.inputs?.add(node)
        }
        for (node in nodes) {
            val tr = node.ifFalse
            tr?.inputs?.add(node)
        }
    }

    private fun removeNodesWithoutInputs() {
        val firstNode = nodes.first()
        while (nodes.removeIf { it != firstNode && it.inputs.isEmpty() }) {
            recalculateInputs()
        }
    }

    private fun removeNodesWithoutCode() {
        for (i in nodes.indices.reversed()) {
            val node = nodes[i]
            if (node.hasNoCode && !node.isBranch) {
                val next = node.next
                val nextNode = labelToNode[next]
                if (nextNode == null || (nextNode.inputStack == node.outputStack)) {
                    for (input in node.inputs) {
                        if (input.ifTrue == node.label) input.ifTrue = next
                        if (input.ifFalse == node) input.ifFalse = labelToNode[next]
                    }
                    if (nextNode != null) {
                        nextNode.inputs.remove(node)
                        nextNode.inputs.addAll(node.inputs)
                    }
                    nodes.removeAt(i)
                }
            }
        }
    }

    private fun validateInputOutputStacks() {
        // check input- and output stacks
        val illegals = ArrayList<String>()
        for (node in nodes) {
            val outputStack = node.outputStack
            val b1 = labelToNode[node.ifTrue]
            if (b1 != null) {
                if (b1.inputStack != outputStack) {
                    illegals += ("$outputStack != ${b1.inputStack}, ${node.index} -> ${b1.index}, ${node.label} -> ${b1.label}")
                }
            }
            val b0 = node.ifFalse
            if (b0 != null) {
                if (b0.inputStack != outputStack) {
                    illegals += ("$outputStack != ${b0.inputStack}, ${node.index} -> ${b0.index}, ${node.label} -> ${b0.label}")
                }
            }
        }

        if (illegals.isNotEmpty()) {
            printState()
            for (ill in illegals) {
                println(ill)
            }
            throw IllegalStateException("Illegal node in $sig")
        }
    }

    fun printState() {
        println()
        println("state: ")
        printState(::println)
    }

    private fun removeNodesWithoutCodeNorBranch() {
        for (i in nodes.indices) {
            val node = nodes.getOrNull(i) ?: break
            val next = node.next
            if (!node.isBranch && next != null && node.hasNoCode) {
                // remove this node from inputs with the next one
                val nextNode = labelToNode[next]
                    ?: throw IllegalStateException("Missing $next")
                for (input in node.inputs) {
                    if (input.ifFalse == node) input.ifFalse = nextNode
                    if (input.ifTrue == node.label) input.ifTrue = next
                }
                // remove this node from outputs
                nextNode.inputs.remove(node)
                nextNode.inputs.addAll(node.inputs)
                nodes.remove(node)
                if (printOps) {
                    println("removed ${node.index} \"${node.printer}\"")
                    printState()
                }
            }
        }
    }


    private fun blockParamsGetParams(from: Node): List<String> {
        return from.inputStack
    }

    private fun blockParamsGetResult(from: Node, to: Node?): List<String> {
        if (to == null) {
            // output must be equal to input
            if (from.inputStack.isNotEmpty()) {
                return from.inputStack
            }
        } else if (from.outputStack.isNotEmpty()) {
            if (!from.isReturn) {
                return from.outputStack
            }
        }// else if(printOps) append(";; no return values found in ${node.index}\n")
        return emptyList()
    }

    private fun makeNodeLoop(name: String, node: Node) {
        val label = "$name${loopIndex++}"
        node.printer.append(Jump(label))
        val newPrinter = Builder()
        newPrinter.append(
            LoopInstr(
                label, node.printer.instrs,
                blockParamsGetResult(node, null)
            )
        ).append(Unreachable)
        node.printer = newPrinter
        node.isAlwaysTrue = false
        node.ifTrue = null
        node.ifFalse = null
        node.isReturn = true // it must return, or iterate infinitely
        node.inputs.remove(node) // it no longer links to itself
    }

    private fun isEasyNode(node: Node): Boolean {
        return node.printer.instrs.count2 { it !is Comment } < 3 &&
                node.printer.instrs.none2 { it is IfBranch || it is SwitchCase || it is LoopInstr }
    }

    private fun replaceSequences(): Boolean {
        var changed2 = false
        do {
            var changed = false
            for (i in 1 until nodes.size) {
                val curr = nodes.getOrNull(i) ?: break
                if (curr.inputs.size == 1) {
                    // merge with previous node
                    val prev = curr.inputs.first()
                    if (prev.isBranch) continue // cannot merge
                    if (prev === curr) continue // cannot merge either
                    // replace inputs with inputs of previous node
                    curr.inputs.clear()
                    curr.inputs.addAll(prev.inputs)
                    curr.inputStack = prev.inputStack
                    // prepend the previous node to this one
                    curr.printer.prepend(prev.printer)
                    // replace links from prev to curr
                    for (input in prev.inputs) {
                        if (input.ifFalse == prev) input.ifFalse = curr
                        if (input.ifTrue == prev.label) input.ifTrue = curr.label
                    }
                    // then delete the previous node
                    nodes.remove(prev)
                    changed = true
                    changed2 = true
                    if (printOps) {
                        printState()
                        println("-${prev.index} by 0")
                    }
                }
            }
        } while (changed)
        return changed2
    }

    private fun removeEmptyIfStatements(): Boolean {
        var changed2 = false
        for (node in nodes) {
            if (node.ifTrue == node.ifFalse?.label && node.ifTrue != null) {
                node.ifTrue = null
                node.printer.drop().comment("ifTrue == ifFalse")
                val ifFalse = node.ifFalse!!.inputs
                ifFalse.remove(node)
                ifFalse.remove(node)
                ifFalse.add(node)
                changed2 = true
            }
        }
        return changed2
    }

    private fun duplicateEasyNodes(): Boolean {
        var changed2 = false
        nodes.removeIf { node ->
            if (node.inputs.isNotEmpty() && node.isReturn && isEasyNode(node)) {
                node.inputs.removeIf { other ->
                    if (!other.isBranch) {
                        other.printer.append(node.printer)
                        other.ifFalse = null
                        other.ifTrue = null
                        other.isReturn = true
                        other.isAlwaysTrue = false
                        other.hasNoCode = false
                        changed2 = true
                        true
                    } else false
                }
                node.inputs.isEmpty()
            } else false
        }
        return changed2
    }

    /**
     * remove dead ends by finding what is reachable
     * */
    private fun removeDeadEnds(): Boolean {
        val reachable = HashSet<Node>()
        val remaining = ArrayList<Node>()

        remaining.add(nodes.first())
        reachable.add(nodes.first())

        while (remaining.isNotEmpty()) {
            val sample = remaining.last()
            remaining.removeAt(remaining.lastIndex)
            val b1 = labelToNode[sample.ifTrue]
            if (b1 != null && reachable.add(b1)) {
                remaining.add(b1)
            }
            val b2 = sample.ifFalse
            if (b2 != null && reachable.add(b2)) {
                remaining.add(b2)
            }
        }

        if (nodes.any { it !in reachable }) {
            val unreachable = nodes.filter { it !in reachable }
            println("warning: found unreachable nodes:")
            for (node in unreachable) {
                println("  $node, ${node.index}, ${node.printer.instrs}")
            }
            //printState()
            val toRemove = unreachable.toSet()
            for (node in nodes) {
                node.inputs.removeAll(toRemove)
            }
            nodes.removeAll(toRemove)
            return true
        } else return false
    }

    private fun findWhileTrueLoops(): Boolean {
        var changed2 = false
        do {
            var changed = false
            for (node in nodes) {
                if (!node.isBranch && !node.isReturn && node.next == node.label) {
                    // replace loop with wasm loop
                    makeNodeLoop("whileTrue", node)
                    // node.printer.append("  unreachable\n")
                    changed = true
                    changed2 = true
                }
            }
        } while (changed)
        return changed2
    }

    private fun findWhileLoops(): Boolean {
        // find while(x) loops
        // A -> A|B
        var changed2 = false
        do {
            var changed = false
            for (node in nodes) {
                if (node.isBranch) {
                    if (node.ifTrue == node.label) {
                        // A ? A : B
                        // -> A -> B
                        // loop(A, if() else break)
                        val loopIdx = loopIndex++
                        val label = "whileA$loopIdx"
                        node.printer.append(JumpIf(label))
                        val printer = LoopInstr(
                            label, ArrayList(node.printer.instrs),
                            blockParamsGetResult(node, null)
                        )
                        node.printer.clear()
                        node.printer.append(printer)
                        node.inputs.remove(node)
                        node.ifTrue = null
                        //printState()
                        changed2 = true
                        changed = true
                    } else if (node.ifFalse == node) {
                        // A ? B : A
                        // loop(A, if() break)
                        val loopIdx = loopIndex++
                        val label = "whileB$loopIdx"
                        node.printer
                            .append(I32EQZ)
                            .append(JumpIf(label))
                        val printer = LoopInstr(
                            label, ArrayList(node.printer.instrs),
                            blockParamsGetResult(node, null)
                        )
                        node.printer.clear()
                        node.printer.append(printer)
                        node.inputs.remove(node)
                        node.ifFalse = null
                        node.isAlwaysTrue = true
                        node.ifTrue!!
                        //printState()
                        changed2 = true
                        changed = true
                    }
                }
            }
        } while (changed)
        return changed2
    }

    private fun replaceSimpleBranch(): Boolean {
        var changed2 = false
        // replace simple branch
        // A -> B|C, B -> C
        do {
            var changed = false
            for (i in nodes.indices) {
                val node = nodes.getOrNull(i) ?: break
                if (node.isBranch) {
                    val b0 = node.ifFalse!!
                    val b1 = labelToNode[node.ifTrue]!!
                    if (!b0.isBranch && b0.inputs.size == 1 && b0.next == b1.label) {
                        // we can make b0 an optional part of node
                        if (node === b0) throw IllegalStateException()
                        node.printer.append(I32EQZ)

                        node.printer.append(
                            IfBranch(
                                ArrayList(b0.printer.instrs), emptyList(),
                                blockParamsGetParams(b0),
                                blockParamsGetResult(b0, null)
                            )
                        )

                        nodes.remove(b0)
                        b1.inputs.remove(b0)
                        // node no longer is a branch
                        node.isAlwaysTrue = true
                        node.ifTrue!!
                        node.ifFalse = null
                        changed = true
                        changed2 = true
                        if (printOps) {
                            printState()
                            println("-${b0.index} by 10")
                        }
                    } else if (!b1.isBranch && b1.inputs.size == 1 && b1.next == b0.label) {
                        // we can make b1 optional
                        if (node === b1) throw IllegalStateException()

                        node.printer.append(
                            IfBranch(
                                ArrayList(b1.printer.instrs), emptyList(),
                                blockParamsGetParams(b1),
                                blockParamsGetResult(b1, null)
                            )
                        )

                        nodes.remove(b1)
                        b0.inputs.remove(b1)
                        if (printOps) println("-${b1.index} by 11")
                        // it will be always false
                        node.isAlwaysTrue = false
                        node.ifTrue = null
                        //printState()
                        changed = true
                        changed2 = true
                    }
                }
            }
        } while (changed)
        return changed2
    }

    private fun mergeGeneralBranching(): Boolean {
        var changed2 = false
        // general branching
        // A -> B|C; B -> D; C -> D
        do {
            var changed = false
            for (i in nodes.indices) {
                val node = nodes.getOrNull(i) ?: break
                if (node.isBranch) {
                    val b0 = node.ifFalse!!
                    val b1 = labelToNode[node.ifTrue]!!
                    if (!b0.isBranch && !b1.isBranch && b0.next == b1.next && b0.next != null &&
                        (ignoreSize || (b0.inputs.size == 1 && b1.inputs.size == 1))
                    ) {
                        // make node into a loop, which branches into b0/b1
                        if (node === b0 || node === b1) throw IllegalStateException()

                        node.printer.append(
                            IfBranch(
                                ArrayList(b1.printer.instrs), ArrayList(b0.printer.instrs),
                                blockParamsGetParams(b1),
                                blockParamsGetResult(b1, b0)
                            )
                        )

                        if (b1.isReturn && b0.isReturn &&
                            !node.printer.endsWith(Return) &&
                            !node.printer.endsWith(Unreachable)
                        ) {
                            node.printer.append(Unreachable)
                        }
                        val end = labelToNode[b0.next]!!
                        node.ifFalse = end
                        node.ifTrue = null
                        node.outputStack = b0.outputStack
                        end.inputs.add(node)
                        end.inputs.remove(b0)
                        end.inputs.remove(b1)
                        if (b0.inputs.size == 1) nodes.remove(b0)
                        else b0.inputs.remove(node)
                        if (b1.inputs.size == 1) nodes.remove(b1)
                        else b1.inputs.remove(node)
                        //printState()
                        changed = true
                        changed2 = true
                    }
                }
            }
        } while (changed)
        return changed2
    }

    private fun canReturnFirstNode(): Boolean {
        return nodes.size == 1 && nodes.first().inputs.isEmpty()
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
        var changed2 = false
        do {
            var changed = false
            for (i in nodes.indices) {
                val node = nodes.getOrNull(i) ?: return changed2
                if (node.isBranch) {
                    val b0 = node.ifFalse!!
                    val b1 = labelToNode[node.ifTrue]!!
                    if (b0.isReturn && b1.isReturn &&
                        (ignoreSize || (b0.inputs.size == 1 && b1.inputs.size == 1))
                    ) {
                        if (node === b0 || node === b1) throw IllegalStateException()

                        // join nodes
                        node.printer.append(
                            IfBranch(
                                ArrayList(b1.printer.instrs), ArrayList(b0.printer.instrs),
                                blockParamsGetParams(b1),
                                blockParamsGetResult(b1, b0)
                            )
                        )

                        if (b1.isReturn && b0.isReturn &&
                            !node.printer.endsWith(Return) &&
                            !node.printer.endsWith(Unreachable)
                        ) node.printer.append(Unreachable)
                        b0.inputs.remove(node)
                        b1.inputs.remove(node)
                        if (b0.inputs.isEmpty()) {
                            nodes.remove(b0)
                        }
                        if (b1.inputs.isEmpty()) {
                            nodes.remove(b1)
                        }
                        node.isReturn = true
                        node.ifTrue = null
                        node.ifFalse = null
                        changed = true
                        changed2 = true
                        if (printOps) {
                            printState()
                            if (b0.inputs.isEmpty())
                                println("-${b0.index} by 1")
                            if (b1.inputs.isEmpty())
                                println("-${b1.index} by 2")
                        }
                    } else if (b0.isReturn && (ignoreSize || b0.inputs.size == 1)) {
                        // remove this branch
                        if (node === b0) throw IllegalStateException()
                        node.printer.append(I32EQZ)

                        node.printer.append(
                            IfBranch(
                                ArrayList(b0.printer.instrs), emptyList(),
                                blockParamsGetParams(b0),
                                blockParamsGetResult(b0, null)
                            )
                        )

                        b0.inputs.remove(node)
                        if (b0.inputs.isEmpty()) {
                            nodes.remove(b0)
                        }
                        node.ifFalse = null
                        node.isAlwaysTrue = true
                        node.ifTrue!!
                        changed = true
                        changed2 = true
                        if (printOps) {
                            printState()
                            if (b0.inputs.isEmpty())
                                println("-${b0.index} by 3 | ${node.index} -> ${b0.index}/${b1.index}")
                        }
                    } else if (b1.isReturn && (ignoreSize || b1.inputs.size == 1)) {
                        // remove this branch
                        if (node === b1) throw IllegalStateException()

                        node.printer.append(
                            IfBranch(
                                ArrayList(b1.printer.instrs), emptyList(),
                                blockParamsGetParams(b1),
                                blockParamsGetResult(b1, null)
                            )
                        )

                        b1.inputs.remove(node)
                        if (b1.inputs.isEmpty()) {
                            nodes.remove(b1)
                        }
                        node.ifTrue = null
                        changed = true
                        changed2 = true
                        if (printOps) {
                            printState()
                            if (b1.inputs.isEmpty())
                                println("-${b1.index} by 4")
                        }
                    }
                }
            }
        } while (changed)
        return changed2
    }

    /**
     * small circle
     * A -> B | C; B -> A
     * */
    private fun findSmallCircle(): Boolean {
        var changed2 = false
        do {
            var changed = false
            for (i in nodes.indices) {

                val nodeA = nodes.getOrNull(i) ?: break
                if (!nodeA.isBranch) continue

                fun isSmallCircle(nextNode: Node): Boolean {
                    return !nextNode.isBranch && !nextNode.isReturn &&
                            nextNode.next == nodeA.label && nextNode.inputs.size == 1
                }

                val ifTrue = labelToNode[nodeA.ifTrue]!!
                val ifFalse = nodeA.ifFalse!!

                val ifTrueIsCircle = isSmallCircle(ifTrue)
                val ifFalseIsCircle = isSmallCircle(ifFalse)

                val nodeB: Node
                val nodeC: Node
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
                changed2 = true

                val label = "smallCircle${loopIndex++}"
                nodeB.printer.append(Jump(label))
                if (negate) nodeA.printer.append(I32EQZ)
                nodeA.printer.append(
                    IfBranch(
                        nodeB.printer.instrs, emptyList(),
                        blockParamsGetParams(nodeB),
                        blockParamsGetResult(nodeB, null)
                    )
                )

                val newPrinter = Builder()
                newPrinter.append(
                    LoopInstr(
                        label, nodeA.printer.instrs,
                        blockParamsGetResult(nodeA, null)
                    )
                )
                nodeA.printer = newPrinter // replace for speed :)

                nodeA.ifTrue = null
                nodeA.ifFalse = nodeC
                nodeA.inputs.remove(nodeB) // it no longer links to next
                nodes.remove(nodeB)
            }
        } while (changed)
        return changed2
    }

    /**
     * merge small circles
     * A -> B; B -> A; only entry: A
     * */
    private fun mergeSmallCircles(): Boolean {
        var changed2 = false
        do {
            var changed = false
            for (i in nodes.indices) {
                val node1 = nodes.getOrNull(i) ?: break
                if (!node1.isReturn && !node1.isBranch) {
                    val node2 = labelToNode[node1.next]!!
                    if (node1 !== node2 && !node2.isReturn && !node2.isBranch && node2.next == node1.label) {
                        // we found such a circle
                        val entry1 = node1.inputs.size > 1
                        val entry2 = node2.inputs.size > 1
                        if (entry1 && entry2) {
                            // TO DO("duplicate functionality")
                            // code duplication -> ignore this case
                        } else if (entry1) {
                            // append entry2 to entry1
                            node1.printer.append(node2.printer)
                            node1.inputs.remove(node2)
                            node1.outputStack = node2.outputStack
                            nodes.remove(node2)
                            if (printOps) println("-${node2.index} by 7")
                            makeNodeLoop("mergeSmallCircleA", node1)
                            changed = true
                            changed2 = true
                        } else if (entry2) {
                            // append entry1 to entry2
                            node2.printer.append(node1.printer)
                            node2.inputs.remove(node1)
                            node2.outputStack = node1.outputStack
                            nodes.remove(node1)
                            if (printOps) println("-${node1.index} by 8")
                            makeNodeLoop("mergeSmallCircleB", node2)
                            changed = true
                            changed2 = true
                        }
                    }
                }
            }
        } while (changed)
        return changed2
    }

    private fun unlinkReturnNodes() {
        for (node in nodes) {
            if (node.isReturn) {
                node.ifTrue = null
                node.ifFalse = null
            }
            if (node.isAlwaysTrue) {
                node.ifFalse = null
            }
        }
    }

    private fun calculateReturnNodes() {
        for (node in nodes) {
            val isReturn = node.printer.lastOrNull()?.isReturning() ?: false
            node.isReturn = isReturn
            if (isReturn) {
                node.ifTrue = null
                node.ifFalse = null
                node.isAlwaysTrue = false
            }
        }
    }

    private fun trySolveLinearTree(): Boolean {
        // todo don't skip getType
        if (sig.name == "getType") return false // let's find an easier graph
        return SolveLinearTree.solve(nodes, this)
    }

    /**
     * transform all nodes into some nice if-else-tree
     * */
    fun joinNodes(): Builder {

        if (printOps) {
            println("\n${sig.clazz} ${sig.name} ${sig.descriptor}: ${nodes.size}")
        }

        val isLookingAtSpecial = false
        if (isLookingAtSpecial) {
            prettyPrint = true
            printOps = true
        }

        lastSize = nodes.size

        assertFalse(nodes.isEmpty())
        if (canReturnFirstNode()) {
            return firstNodeForReturn()
        }

        calculateReturnNodes()

        for (node in nodes) {
            node.hasNoCode = node.calcHasNoCode()
            removeUselessBranches(node)
        }

        renumber(nodes) // just for debug printing

        unlinkReturnNodes()

        recalculateInputs()
        removeNodesWithoutInputs()

        removeNodesWithoutCode()
        // removeDuplicateNodes()
        renumber(nodes)

        validateInputOutputStacks()

        if (printOps) printState()

        removeNodesWithoutCodeNorBranch()

        checkState()

        if (canReturnFirstNode()) {
            return firstNodeForReturn()
        }

        StackValidator.validateStack(nodes, methodTranslator)

        while (true) {
            var changed2 = false
            for (i in 0 until 11) {
                val changed2i = when (i) {
                    0 -> removeEmptyIfStatements()
                    1 -> duplicateEasyNodes()
                    2 -> replaceSequences()
                    3 -> removeDeadEnds()
                    4 -> findWhereBothBranchesTerminate()
                    5 -> findWhileTrueLoops()
                    6 -> findWhileLoops()
                    7 -> replaceSimpleBranch()
                    8 -> mergeGeneralBranching()
                    9 -> mergeSmallCircles()
                    10 -> findSmallCircle()
                    else -> false
                }
                if (changed2i) {
                    checkState()
                    if (canReturnFirstNode()) {
                        return firstNodeForReturn()
                    }
                    changed2 = true
                }
            }

            if (!changed2 && trySolveLinearTree()) {
                assertTrue(canReturnFirstNode())
                return firstNodeForReturn()
            }

            if (!changed2) {
                if (printOps) printState()
                assertFalse(isLookingAtSpecial, "Looking at something")
                methodTranslator.localVariables1.add(LocalVariable("lbl", i32))
                return createLargeSwitchStatement2(nodes, this)
            }
        }

        // to do solve this problem
        // possible ways:
        // - implement Dream from "No More Gotos: Decompilation Using Pattern-Independent Control-Flow Structuring and Semantics-Preserving Transformations"
        //      this apparently is implemented in https://github.com/fay59/fcd
        //      it's discussed at https://stackoverflow.com/questions/27160506/decompiler-how-to-structure-loops

        // - implement sub-functions that jump to each other
        // - implement a large switch-case statement, that loads the stack, stores the stack, and sets the target label for each remaining round 😅

        // if we're here, it's really complicated,
        // and there isn't any easy ends
    }

    private val stackVariables = HashSet<String>()
    fun getStackVarName(i: Int, type: String): String {
        val name = "s$i$type"
        if (stackVariables.add(name)) {
            methodTranslator.localVariables1.add(LocalVariable(name, type))
            methodTranslator.localVarsWithParams.add(LocalVar("?", type, name, -1, false))
        }
        return name
    }
}

