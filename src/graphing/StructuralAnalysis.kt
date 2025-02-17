package graphing

import me.anno.utils.structures.tuples.IntPair
import org.objectweb.asm.Label
import utils.Builder
import utils.MethodSig
import utils.methodName
import wasm.instr.*
import wasm.instr.Const.Companion.i32Const
import wasm.instr.Instructions.Drop
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
import java.io.FileOutputStream
import kotlin.math.min

object StructuralAnalysis {

    var comments = false

    private var prettyPrint = false
    private var loopIndex = 0
    var printOps = prettyPrint
    var ignoreSize = false
    var compactStatePrinting = true

    var lastSize = 0

    fun printState(nodes: List<Node>, labelToNode: Map<Label, Node>, printLine: (String) -> Unit) {

        for (node in nodes) {
            val name = node.toString { label ->
                labelToNode[label]?.index.toString()
            }
            if (compactStatePrinting) {
                printLine(
                    "${"${name},".padEnd(15)}#${
                        node.inputs.map { it.index }.sorted()
                    }, ${node.inputStack} -> ${node.outputStack}, ${
                        node.printer.toString().replace('\n', '|')
                    }"
                )
            } else {
                printLine(
                    "${"${name},".padEnd(15)}#${
                        node.inputs.map { it.index }.sorted()
                    }, ${node.inputStack} -> ${node.outputStack}"
                )
                printLine("")
                for (line in node.printer.instr) {
                    var ln = line
                    //for (node1 in nodes) {
                    //    ln = ln.replace(node1.label.toString(), "[${node1.index}]")
                    //}
                    printLine("             $ln")
                }
            }
        }
    }

    fun checkState(nodes: List<Node>, labelToNode: Map<Label, Node>) {
        for (node in nodes) {
            val tr = labelToNode[node.ifTrue]
            if (tr != null && tr !in nodes) {
                printState(nodes, labelToNode, ::println)
                throw IllegalStateException("${node.index} -> ${tr.index}")
            }
            val fs = node.ifFalse
            if (fs != null && fs !in nodes) {
                printState(nodes, labelToNode, ::println)
                throw IllegalStateException("${node.index} -> ${fs.index}")
            }
        }
    }

    /**
     * transform all nodes into some nice if-else-tree
     * */
    fun transform(sig: MethodSig, nodes: MutableList<Node>): String {

        val isLookingAtSpecial = false
        if (isLookingAtSpecial) {
            prettyPrint = true
            printOps = true
        }

        lastSize = nodes.size

        if (nodes.isEmpty()) throw IllegalArgumentException()
        if (nodes.size == 1 && nodes[0].inputs.isEmpty())
            return nodes[0].printer.toString()

        val labelToNode = HashMap(nodes.associateBy { it.label })

        for (node in nodes) {
            node.hasNoCode = node.calcHasNoCode()
            if (node.isBranch) {
                while (node.printer.endsWith(I32EQZ)) {// remove unnecessary null check before branch
                    node.printer.instr.removeLast()
                    // while (node.printer.endsWith(" ")) node.printer.size-- // remove spaces
                    val t = node.ifTrue!!
                    val f = node.ifFalse!!
                    node.ifTrue = f.label
                    node.ifFalse = labelToNode[t]!!
                }
            }
        }

        for (index in nodes.indices) {
            nodes[index].index = index
        }
        var nextNodeIndex = nodes.size

        fun printState() {
            println()
            println("state: ")
            printState(nodes, labelToNode, ::println)
        }

        for (node in nodes) {
            if (node.isReturn) {
                node.ifTrue = null
                node.ifFalse = null
            }
            if (node.isAlwaysTrue) {
                node.ifFalse = null
            }
        }

        // add branching information to all nodes
        fun recalculateInputs() {

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
        recalculateInputs()

        // remove nodes without input
        while (nodes.removeIf {
                it != nodes.first() && it.inputs.isEmpty()
            }) {
            recalculateInputs()
        }

        // remove nodes without code
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

        val uniqueNodes = HashMap<Triple<String, IntPair, List<String>?>, Node>()
        val nodeMap = HashMap<Node, Node>()
        for (node in nodes) {
            val key = Triple(
                node.printer.toString(),
                IntPair(
                    labelToNode[node.ifTrue]?.index ?: -1,
                    node.ifFalse?.index ?: -1
                ),
                node.inputStack
            )
            val other = uniqueNodes[key]
            if (other != null) {
                nodeMap[node] = other
                other.inputs.addAll(node.inputs)
            } else {
                uniqueNodes[key] = node
            }
        }
        if (nodeMap.isNotEmpty()) {
            nodes.removeIf { it in nodeMap.keys }
            for (node in nodes) {
                node.inputs.addAll(node.inputs.map { nodeMap[it] ?: it })
                node.inputs.removeIf { it in nodeMap.keys }
                if (node.ifTrue != null) {
                    val ifTrue = labelToNode[node.ifTrue]
                    node.ifTrue = nodeMap[ifTrue]?.label ?: node.ifTrue
                }
                if (node.ifFalse in nodeMap) {
                    node.ifFalse = nodeMap[node.ifFalse]!!
                }
            }
        }

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

        if (printOps) printState()

        for (i in nodes.indices) {
            val node = nodes.getOrNull(i) ?: break
            val next = node.next
            if (!node.isBranch && next != null && node.hasNoCode) {
                // remove this node from inputs with the next one
                val nextNode = labelToNode[next]
                if (nextNode == null) {
                    printState()
                    throw IllegalStateException("Missing node for $next by ${node.index}, ${nodes.any { it.label == next }}")
                }
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

        fun checkState() {
            checkState(nodes, labelToNode)
        }

        while (true) {

            var changed2 = false

            fun blockParamsGetParams(from: Node): List<String> {
                return from.inputStack!!
            }

            fun blockParamsGetResult(from: Node, to: Node?): List<String> {
                if (to == null) {
                    // output must be equal to input
                    if (from.inputStack?.isNotEmpty() == true) {
                        return from.inputStack!!
                    }
                } else if (from.outputStack?.isNotEmpty() == true) {
                    if (!from.isReturn) {
                        return from.outputStack!!
                    }
                }// else if(printOps) append(";; no return values found in ${node.index}\n")
                return emptyList()
            }

            fun makeNodeLoop(node: Node) {
                val newPrinter = Builder(node.printer.length + 32)
                val loopIdx = loopIndex++
                val label = "b$loopIdx"
                node.printer.instr.add(Jump(label))
                newPrinter.append(
                    LoopInstr(
                        label, ArrayList(node.printer.instr),
                        blockParamsGetResult(node, null)
                    )
                )
                node.printer.clear()
                node.printer.append(newPrinter)
                node.printer.append(Unreachable)
                node.isAlwaysTrue = false
                node.ifTrue = null
                node.ifFalse = null
                node.isReturn = true // it must return, or iterate infinitely
                node.inputs.remove(node) // it no longer links to itself
                changed2 = true
                //printState()
            }

            // handle empty if-statements, because they are really easy
            for (node in nodes) {
                if (node.ifTrue == node.ifFalse?.label && node.ifTrue != null) {
                    node.ifTrue = null
                    node.printer.append(Drop).comment("ifTrue == ifFalse")
                    val ifFalse = node.ifFalse!!.inputs
                    ifFalse.remove(node)
                    ifFalse.remove(node)
                    ifFalse.add(node)
                    changed2 = true
                }
            }

            checkState()

            // allow duplication for these easy pieces of code
            nodes.removeIf { node ->
                if (node.inputs.isNotEmpty() && node.isReturn && (
                            node.printer.equals("  i32.const 0\n  return\n") ||
                                    node.printer.equals("  i32.const 0\n  i32.const 0\n  return\n") ||
                                    node.printer.equals("  i32.const 1\n  i32.const 0\n  return\n")
                            )
                ) {
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

            checkState()

            // first the easy part: replace sequences
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

            checkState()

            if (nodes.size == 1 && nodes[0].inputs.isEmpty()) {
                return nodes[0].printer.toString()
            }

            if (true) {
                // remove dead ends by finding what is reachable
                val reachable = HashSet<Node>()
                val open = ArrayList<Node>()

                open.add(nodes.first())
                reachable.add(nodes.first())

                while (open.isNotEmpty()) {
                    val sample = open.last()
                    open.removeAt(open.lastIndex)
                    val b1 = labelToNode[sample.ifTrue]
                    if (b1 != null && reachable.add(b1)) {
                        open.add(b1)
                    }
                    val b2 = sample.ifFalse
                    if (b2 != null && reachable.add(b2)) {
                        open.add(b2)
                    }
                }

                if (nodes.any { it !in reachable }) {
                    val unreachable = nodes.filter { it !in reachable }
                    println("warning: found unreachable nodes:")
                    for (node in unreachable) {
                        println("  $node, ${node.index}, ${node.printer}")
                    }
                    //printState()
                    changed2 = true
                    val toRemove = unreachable.toSet()
                    for (node in nodes) {
                        node.inputs.removeAll(toRemove)
                    }
                    nodes.removeAll(toRemove)
                    if (nodes.size == 1) return nodes[0].printer.toString()
                }
            }

            checkState()

            // next easy step: if both branches terminate, we can join this, and mark it as ending
            // if a branch terminates, we can just add this branch without complications
            do {
                var changed = false
                for (i in nodes.indices) {
                    val node = nodes.getOrNull(i) ?: break
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
                                    ArrayList(b1.printer.instr), ArrayList(b0.printer.instr),
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
                                    ArrayList(b0.printer.instr), emptyList(),
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
                                    ArrayList(b1.printer.instr), emptyList(),
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

            checkState()

            if (nodes.size == 1 && nodes[0].inputs.isEmpty())
                return nodes[0].printer.toString()

            // find while-true loops
            do {
                var changed = false
                for (node in nodes) {
                    if (!node.isBranch && !node.isReturn && node.next == node.label) {
                        // replace loop with wasm loop
                        makeNodeLoop(node)
                        // node.printer.append("  unreachable\n")
                        changed = true
                        changed2 = true
                    }
                }
            } while (changed)

            checkState()

            // find while(x) loops
            // A -> A|B
            do {
                var changed = false
                for (node in nodes) {
                    if (node.isBranch) {
                        if (node.ifTrue == node.label) {
                            // A ? A : B
                            // -> A -> B
                            // loop(A, if() else break)
                            val printer = Builder(node.printer.length + 20)
                            val loopIdx = loopIndex++
                            val label = "b$loopIdx"
                            node.printer.append(JumpIf(label))
                            printer.append(
                                LoopInstr(
                                    label, ArrayList(node.printer.instr),
                                    blockParamsGetResult(node, null)
                                )
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
                            val printer = Builder(node.printer.length + 20)
                            val loopIdx = loopIndex++
                            val label = "b$loopIdx"
                            node.printer
                                .append(I32EQZ)
                                .append(JumpIf(label))
                            printer.append(
                                LoopInstr(
                                    label, ArrayList(node.printer.instr),
                                    blockParamsGetResult(node, null)
                                )
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

            checkState()

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
                                    ArrayList(b0.printer.instr), emptyList(),
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
                                    ArrayList(b1.printer.instr), emptyList(),
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

            checkState()

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
                                    ArrayList(b1.printer.instr), ArrayList(b0.printer.instr),
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

            checkState()

            // merge small circles
            // A -> B; B -> A; only entry: A
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
                                makeNodeLoop(node1)
                                changed = true
                                changed2 = true
                            } else if (entry2) {
                                // append entry1 to entry2
                                node2.printer.append(node1.printer)
                                node2.inputs.remove(node1)
                                node2.outputStack = node1.outputStack
                                nodes.remove(node1)
                                if (printOps) println("-${node1.index} by 8")
                                makeNodeLoop(node2)
                                changed = true
                                changed2 = true
                            }
                        }
                    }
                }
            } while (changed)

            checkState()

            if (changed2) continue

            // small circle
            // A -> B | C; B -> A
            do {
                var changed = false
                for (i in nodes.indices) {
                    val nA = nodes.getOrNull(i) ?: break
                    if (nA.isBranch) {
                        val nI = labelToNode[nA.ifTrue]!!
                        val nJ = nA.ifFalse!!
                        fun handle(insideLoop: Node, other: Node, negate: Boolean) {

                            changed = true
                            changed2 = true

                            val newPrinter = Builder(nA.printer.length + insideLoop.printer.length + 32)
                            val loopIdx = loopIndex++
                            val label = "b$loopIdx"
                            if (negate) insideLoop.printer.append(I32EQZ)
                            insideLoop.printer.append(Jump(label))
                            nA.printer.append(
                                IfBranch(
                                    ArrayList(insideLoop.printer.instr), emptyList(),
                                    blockParamsGetParams(insideLoop),
                                    blockParamsGetResult(insideLoop, null)
                                )
                            )
                            newPrinter.append(
                                LoopInstr(
                                    label, ArrayList(nA.printer.instr),
                                    blockParamsGetResult(nA, null)
                                )
                            )
                            nA.printer = newPrinter // replace for speed :)
                            nA.ifTrue = null
                            nA.ifFalse = other
                            nA.inputs.remove(insideLoop) // it no longer links to next
                            nodes.remove(insideLoop)

                        }

                        val n2i = !nI.isBranch && !nI.isReturn &&
                                nI.next == nA.label && nI.inputs.size == 1
                        val n2j = !nJ.isBranch && !nJ.isReturn &&
                                nJ.next == nA.label && nJ.inputs.size == 1
                        if (n2i && n2j) {
                            // to do handle infinite loop
                            // not happening in 4.2k classes, so probably not that important...
                        } else if (n2i) {
                            handle(nI, nJ, false)
                        } else if (n2j) {
                            handle(nJ, nI, true)
                        }
                    }
                }
            } while (changed)

            checkState()

            if (changed2) continue

            checkState()

            // to do solve this problem
            // possible ways:
            // - implement Dream from "No More Gotos: Decompilation Using Pattern-Independent Control-Flow Structuring and Semantics-Preserving Transformations"
            //      this apparently is implemented in https://github.com/fay59/fcd
            //      it's discussed at https://stackoverflow.com/questions/27160506/decompiler-how-to-structure-loops

            // - implement sub-functions that jump to each other
            // - implement a large switch-case statement, that loads the stack, stores the stack, and sets the target label for each remaining round 😅

            // if we're here, it's really complicated,
            // and there isn't any easy ends

            if (printOps) printState()

            if (isLookingAtSpecial) {
                throw IllegalStateException("Looking at something")
            }

            return createLargeSwitchStatement(sig, nodes, labelToNode)
        }
    }

    val equalPairs = arrayOf(
        I32LTS, I32GES,
        I32LES, I32GTS,

        // todo why is this an issue?
        // "i32.eq\n", "i32.ne\n",
        I32EQ, I32NE,

        I64LTS, I64GES,
        I64LES, I64GTS,
        I64EQ, I64NE,
        // nez doesn't exist :/
        // "i32.eqz\n", "i32.nez\n",
        // "i64.eqz\n", "i64.nez\n",
    )

    private fun normalizeGraph(nodes: List<Node>, labelToNode: Map<Label, Node>) {
        var changed = false
        // printState(nodes, labelToNode, ::println)
        for (node in nodes) {
            if (node.isBranch) {
                val ifTrue = labelToNode[node.ifTrue]!!
                val ifFalse = node.ifFalse!!
                if (ifTrue.index > ifFalse.index) {
                    swapBranches(node, ifTrue, ifFalse)
                    changed = true
                }
            }
        }
        // if (changed) printState(nodes, labelToNode, ::println)
    }

    fun swapBranches(node: Node, ifTrue: Node, ifFalse: Node) {
        val printer = node.printer
        // swap branches :)
        node.ifTrue = ifFalse.label
        node.ifFalse = ifTrue
        // swap conditional
        fun replaceLast(b: Instruction) {
            printer.instr.removeLast()
            printer.append(b)
            printer.comment("xxx")
        }
        for (i in equalPairs.indices step 2) {
            val a = equalPairs[i]
            val b = equalPairs[i + 1]
            if (printer.endsWith(a)) {
                replaceLast(b)
                return
            } else if (printer.endsWith(b)) {
                replaceLast(a)
                return
            }
        }
        /* if (printer.endsWith("i32.eqz\n")) {
             // already negated :)
             printer.size -= "i32.eqz\n".length
             printer.append(" ;; xxx\n")
             return
         }*/
        // negation :)
        printer.append(I32EQZ)
    }

    private fun graphId(sig: MethodSig, nodes: List<Node>, labelToNode: Map<Label, Node>): String {
        // return methodName(sig).shorten(50).toString() + ".txt"
        if (false) normalizeGraph(nodes, labelToNode)
        val builder = StringBuilder(nodes.size * 5)
        for (node in nodes) {
            builder.append(
                when {
                    node.isAlwaysTrue -> 'T'
                    node.isReturn -> 'R'
                    node.isBranch -> 'B'
                    else -> 'N'
                }
            )
            val a = labelToNode[node.ifTrue]?.index
            val b = node.ifFalse?.index
            if (a != null) builder.append(a)
            if (b != null) {
                if (a != null) builder.append('-')
                builder.append(b)
            }
        }
        val maxLength = 80
        if (builder.length > maxLength) {
            val hash = builder.toString().hashCode()
            val extra = builder.substring(0, maxLength - 8)
            builder.clear()
            builder.append(extra)
            builder.append('-')
            builder.append(hash.toUInt().toString(16))
        }
        builder.append(".txt")
        return builder.toString()
    }

    val folder = File(System.getProperty("user.home"), "Desktop/Graphs")

    init {
        folder.mkdir()
    }

    private fun renumber(nodes: List<Node>, exitNode: Node? = null) {
        if (printOps) println("renumbering:")
        var j = 0
        for (node in nodes) {
            // if (node.hasNoCode()) throw IllegalStateException()
            if (printOps) println("${node.index} -> $j")
            node.index = j++
        }
        if (exitNode != null) exitNode.index = j
    }

    private fun createLargeSwitchStatement(sig: MethodSig, nodes0: List<Node>, labelToNode: Map<Label, Node>): String {
        val vs = StackVariables()
        vs.varPrinter.localVariables.add(LocalVariable("lbl", "i32"))
        val code = createLargeSwitchStatement1(sig, nodes0, labelToNode, vs)
        for (i in code.lastIndex downTo 0) {
            vs.varPrinter.append(code[i])
        }
        return vs.varPrinter.toString()
    }

    private fun createLargeSwitchStatement2(
        sig: MethodSig,
        nodes0: List<Node>,
        labelToNode: Map<Label, Node>,
        exitNode: Node?, // not included, next
        vars: StackVariables
    ): Builder {

        // if (nodes.size == 2) throw IllegalStateException()

        val firstNode = nodes0.first()
        val firstIsLinear = !firstNode.isBranch && firstNode.inputs.isEmpty()
        val nodes = if (firstIsLinear) nodes0.subList(1, nodes0.size) else nodes0

        renumber(nodes, exitNode)

        // create graph id
        // to do store image of graph based on id
        val graphId = graphId(sig, nodes, labelToNode)
        if (true) {
            val file = File(folder, graphId)
            if (!file.exists()) {
                val builder = StringBuilder()
                builder.append(sig).append('\n')
                builder.append(methodName(sig)).append('\n')
                printState(nodes, labelToNode) { builder.append(it).append('\n') }
                file.writeText(builder.toString())
            } else {
                val builder = StringBuilder()
                builder.append(sig).append('\n')
                builder.append(methodName(sig)).append('\n')
                val str = builder.toString()
                if (str !in file.readText()) {
                    FileOutputStream(file, true).use {
                        it.write(str.toByteArray())
                    }
                }
            }
        }

        // create large switch-case-statement
        // https://musteresel.github.io/posts/2020/01/webassembly-text-br_table-example.html

        val printer = Builder(nodes.sumOf { it.printer.length + 20 })
        fun finishBlock(node: Node) {
            val printer = node.printer
            if (node.isReturn) {
                printer.append(Unreachable)
            } else {
                // todo if either one is exitNode, jump to end of switch() block
                if (node.isBranch) {
                    // set end label
                    val trueIndex = labelToNode[node.ifTrue]!!.index
                    val falseIndex = node.ifFalse!!.index
                    printer.append(
                        IfBranch(
                            listOf(i32Const(trueIndex)),
                            listOf(i32Const(falseIndex)), emptyList(), listOf("i32")
                        )
                    ).append(LocalSet("lbl"))
                } else {
                    // set end label
                    val next = labelToNode[node.next]!!.index
                    printer.append(i32Const(next)).append(LocalSet("lbl"))
                }
                // store stack
                val outputs = node.outputStack
                if (!outputs.isNullOrEmpty()) {
                    if (comments) printer.comment("store stack")
                    for ((idx, type) in outputs.withIndex().reversed()) {
                        printer.append(LocalSet(vars.getStackVarName(idx, type)))
                    }
                }
            }
        }

        val loopIdx = loopIndex++

        if (firstIsLinear) {
            if (comments) printer.comment("execute -1")
            printer.prepend(firstNode.printer)
            finishBlock(firstNode)
        } else {
            printer
                .append(i32Const(nodes.first().index))
                .append(LocalSet("lbl"))
        }

        printer.comment("#$graphId")

        fun appendBlock(node: Node) {
            var dropCtr = 0
            while (true) {
                if (!node.printer.startsWith(Drop, dropCtr)) break
                dropCtr++
            }
            // load stack
            val inputs = node.inputStack
            if (!inputs.isNullOrEmpty()) {
                if (comments) printer.comment("load stack")
                for (idx in 0 until inputs.size - dropCtr) {
                    val type = inputs[idx]
                    printer.append(LocalGet(vars.getStackVarName(idx, type)))
                }
                val dropped = min(inputs.size, dropCtr)
                if (dropped > 0) {
                    // todo drop variables (?)
                }
            }
            if (node != exitNode) {
                // execute
                if (comments) printer.comment("execute ${node.index}")
                finishBlock(node)
                // close block
                printer.append(Jump("b$loopIdx"))
            }
        }

        for (node in nodes) {
            appendBlock(node)
        }

        printer.append(SwitchCase(nodes.map { it.printer.instr }))

        if (exitNode != null) {
            appendBlock(exitNode)
        } else {
            // close loop
            printer.append(Unreachable)
        }
        return printer
    }

    /// todo there is a small set of near-exit nodes, that we are allowed to replicate: return true/false

    private fun createLargeSwitchStatement1(
        sig: MethodSig,
        nodes0: List<Node>,
        labelToNode: Map<Label, Node>,
        vars: StackVariables
    ): ArrayList<Builder> {
        // find all nodes, that separate the graph
        for (separator in nodes0) {
            if (separator !== nodes0.first() && !separator.isReturn) {

                val reached = HashSet<Node>()
                fun add(node: Node, force: Boolean) {
                    if ((reached.add(node) && node != separator) || force) {
                        val t = labelToNode[node.ifTrue]
                        val f = node.ifFalse
                        if (t != null) add(t, false)
                        if (f != null) add(f, false)
                    }
                }

                add(separator, true)

                if (separator !in reached) {
                    // all other nodes must reach this node
                    val revReached = HashSet(reached)
                    reached.clear()
                    // track backwards
                    fun addReverse(node: Node) {
                        if (reached.add(node)) {
                            for (src in node.inputs) {
                                addReverse(src)
                            }
                        }
                    }
                    addReverse(separator)
                    val size0 = reached.size
                    val size1 = revReached.size
                    if (size0 + size1 == nodes0.size) {
                        reached.addAll(revReached)
                        if (reached.size == nodes0.size) {
                            renumber(nodes0)
                            // println("\nGraph for separation (on ${separator.index}):")
                            // printState(nodes0, labelToNode, ::println)
                            reached.removeAll(revReached)

                            val code0 = createLargeSwitchStatement2(
                                sig, nodes0.filter { it in reached },
                                labelToNode,
                                separator,
                                vars
                            )

                            val code1i = createLargeSwitchStatement1( // split second part :)
                                sig, // first separator (enter node), then all else
                                listOf(separator) + nodes0.filter { it in revReached && it != separator },
                                labelToNode,
                                vars
                            )

                            code1i.add(code0)
                            return code1i
                        }
                    }
                }
            }
        }

        return arrayListOf(createLargeSwitchStatement2(sig, nodes0, labelToNode, null, vars))
    }
}

