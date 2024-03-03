package graphing

import insn.Drop
import me.anno.utils.files.Files.formatFileSize
import me.anno.utils.structures.tuples.IntPair
import org.objectweb.asm.Label
import utils.Builder
import utils.MethodSig
import utils.methodName
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

    private fun Builder.appendIndent(str: Builder): Builder {
        try {
            if (prettyPrint) {
                for ((idx, line) in str.split('\n').withIndex()) {
                    if (idx > 0) append('\n')
                    append("  ").append(line)
                }
            } else append(str)
        } catch (e: OutOfMemoryError) {
            val runtime = Runtime.getRuntime()
            println(
                "used: ${(runtime.totalMemory() - runtime.freeMemory()).formatFileSize(1024)}, " +
                        "free: ${runtime.freeMemory().formatFileSize(1024)}, " +
                        "length: ${length.toLong().formatFileSize(1024)}, " +
                        "to append: ${str.length.toLong().formatFileSize(1024)}"
            )
            throw e
        }
        return this
    }

    private fun Builder.appendIndent2(str: Builder): Builder {
        try {
            if (prettyPrint) {
                for ((idx, line) in str.split('\n').withIndex()) {
                    if (idx > 0) append('\n')
                    append("    ").append(line)
                }
            } else append(str)
        } catch (e: OutOfMemoryError) {
            val runtime = Runtime.getRuntime()
            println(
                "used: ${(runtime.totalMemory() - runtime.freeMemory()).formatFileSize(1024)}, " +
                        "free: ${runtime.freeMemory().formatFileSize(1024)}, " +
                        "length: ${length.toLong().formatFileSize(1024)}, " +
                        "to append: ${str.length.toLong().formatFileSize(1024)}"
            )
            throw e
        }
        return this
    }

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
                for (line in node.printer.split('\n')) {
                    var ln = line
                    for (node1 in nodes) {
                        ln = ln.replace(node1.label.toString(), "[${node1.index}]")
                    }
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
                while (node.printer.endsWith("i32.eqz\n")) {// remove unnecessary null check before branch
                    node.printer.size = node.printer.size - "i32.eqz\n".length
                    while (node.printer.endsWith(" ")) node.printer.size-- // remove spaces
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
            throw IllegalStateException()
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

            fun Builder.blockParams(inputs: List<String>?, outputs: List<String>?, postfix: String): Builder {
                // write params and result
                if (inputs?.isNotEmpty() == true) {
                    append(" (param")
                    for (input in inputs) {
                        append(' ')
                        append(input)
                    }
                    append(')')
                }
                if (outputs != null) {
                    append(" (result")
                    for (output in outputs) {
                        append(' ')
                        append(output)
                    }
                    append(')')
                }// else if(printOps) append(";; no return values found in ${node.index}\n")
                append(" ")
                append(postfix)
                append('\n')
                return this
            }

            fun Builder.blockParams(from: Node, to: Node?, postfix: String, id: String): Builder {
                // write params and result
                if (from.inputStack?.isNotEmpty() == true) {
                    append(" (param")
                    for (input in from.inputStack!!) {
                        append(' ')
                        append(input)
                    }
                    append(')')
                }
                if (to == null) {
                    // output must be equal to input
                    if (from.inputStack?.isNotEmpty() == true) {
                        append(" (result")
                        for (input in from.inputStack!!) {
                            append(' ')
                            append(input)
                        }
                        append(')')
                    }
                } else if (from.outputStack?.isNotEmpty() == true) {
                    if (!from.isReturn) {
                        append(" (result")
                        for (output in from.outputStack!!) {
                            append(' ')
                            append(output)
                        }
                        append(')')
                    } else if (printOps) append(";; skipped return, because exit\n")
                }// else if(printOps) append(";; no return values found in ${node.index}\n")
                append(" ")
                append(postfix)
                append('\n')
                return this
            }

            fun makeNodeLoop(node: Node) {
                val newPrinter = Builder(node.printer.length + 32)
                val loopIdx = loopIndex++
                newPrinter.append("  (loop \$b$loopIdx")
                    .blockParams(node, null, "", "l308")
                    .appendIndent(node.printer)
                    .append("br \$b$loopIdx)\n")
                node.printer.clear()
                node.printer.append(newPrinter)
                node.printer.append("  unreachable\n")
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
                    node.printer.append("  drop ;; ifTrue == ifFalse\n")
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
                            node.printer.append("  (if")
                                .blockParams(b1, b0, "(then", "l457")
                                .appendIndent(b1.printer)
                                .append(") (else\n")
                                .appendIndent(b0.printer)
                                .append("))\n")
                            if (b1.isReturn && b0.isReturn &&
                                !node.printer.endsWith("return\n") &&
                                !node.printer.endsWith("unreachable\n")
                            ) node.printer.append("  unreachable\n")
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
                            node.printer.append("  i32.eqz\n")
                            node.printer.append("  (if")
                                .blockParams(b0, null, "(then", "l491")
                                .appendIndent(b0.printer)
                                .append("))\n")
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
                            node.printer.append("  (if")
                                .blockParams(b1, null, "(then", "l512")
                                .appendIndent(b1.printer)
                                .append(") (else))\n")
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
                            printer.append("  (loop \$b$loopIdx")
                                .blockParams(node, null, "", "l566")
                                .appendIndent(node.printer)
                                .append("br_if \$b$loopIdx)\n")
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
                            printer.append("  (loop \$b$loopIdx")
                                .blockParams(node, null, "", "l582")
                                .appendIndent(node.printer)
                                .append("i32.eqz br_if \$b$loopIdx)")
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
                            node.printer.append("  i32.eqz\n")
                            node.printer.append("  (if")
                                .blockParams(b0, null, "(then", "l615")
                                .appendIndent(b0.printer)
                                .append("))\n")
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
                            node.printer.append("  (if")
                                .blockParams(b1, null, "(then", "l634")
                                .appendIndent(b1.printer)
                                .append(") (else))\n")
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
                            node.printer
                                .append("  (if")
                                .blockParams(b1, b0, "(then", "l669")
                                .appendIndent(b1.printer)
                                .append(") (else\n")
                                .appendIndent(b0.printer)
                                .append("))\n")
                            if (b1.isReturn && b0.isReturn &&
                                !node.printer.endsWith("return\n") &&
                                !node.printer.endsWith("unreachable\n")
                            ) {
                                node.printer.append("  unreachable\n")
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
                            newPrinter.append("  (loop \$b$loopIdx")
                                .blockParams(nA.inputStack, nA.outputStack, "")
                                .appendIndent(nA.printer)
                            nA.printer = newPrinter // replace for speed :)
                            if (negate) nA.printer.append("  i32.eqz\n")
                            nA.printer.append("  (if")
                                .blockParams(insideLoop.inputStack, insideLoop.inputStack, "(then")
                                .appendIndent2(insideLoop.printer)
                                .append("  br \$b$loopIdx)))\n")
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

            // code duplication :/ ; only allowed for short methods
            if (false) if (nodes.sumOf { it.printer.length } < 1024) for (i in nodes.indices.reversed()) { // prefer later nodes first
                val node = nodes.getOrNull(i) ?: break
                if (node.isBranch) {
                    // check if there is a branch, where all sub-branches have the same exit
                    var exit: Label? = null
                    try {
                        val doneNodes = HashSet<Node>() // because we can have loops
                        fun hasSameExit(node: Node) {
                            if (node.isReturn) {
                                if (exit == node.label)
                                    return // ok, same exit :)
                                if (exit == null) {
                                    exit = node.label // ok, found first exit
                                    return
                                }
                                throw FoundEnd // no
                            }
                            val b1 = labelToNode[node.ifTrue]
                            if (b1 != null && doneNodes.add(b1)) {
                                hasSameExit(b1)
                            }
                            val b0 = node.ifFalse
                            if (b0 != null && doneNodes.add(b0)) {
                                hasSameExit(b0)
                            }
                        }
                        hasSameExit(node.ifFalse!!)
                        val falseSize = doneNodes.size
                        doneNodes.clear()
                        hasSameExit(labelToNode[node.ifTrue]!!)
                        if (exit == null) {
                            println("no exit found -> infinity loop?")
                            throw FoundEnd
                        }

                        val trueSize = doneNodes.size

                        fun createNewNode(oldNode: Node, mapping: HashMap<Label, Label>): Node {
                            val label = Label()
                            val newNode = Node(label)
                            mapping[label] = label
                            mapping[oldNode.label] = label
                            labelToNode[label] = newNode
                            newNode.index = nextNodeIndex++
                            newNode.hasNoCode = oldNode.hasNoCode
                            // if (nextNodeIndex > 240) throw IllegalStateException()
                            nodes.add(newNode)
                            return newNode
                        }

                        // if so, break up that branch by copy/paste
                        fun copyWithNewLabels1(oldNode: Node?, mapping: HashMap<Label, Label>): Node? {
                            if (oldNode == null) return null
                            if (oldNode.label in mapping) return oldNode // done :)

                            // not done, generate new node with new label
                            val newNode = createNewNode(oldNode, mapping)

                            // replace all references
                            newNode.ifFalse =
                                if (oldNode.ifFalse != null) copyWithNewLabels1(oldNode.ifFalse, mapping) else null
                            newNode.ifTrue =
                                if (oldNode.ifTrue != null) copyWithNewLabels1(
                                    labelToNode[oldNode.ifTrue]!!,
                                    mapping
                                )!!.label else null
                            // copy all data
                            newNode.isAlwaysTrue = oldNode.isAlwaysTrue
                            newNode.isReturn = oldNode.isReturn
                            newNode.inputStack = oldNode.inputStack
                            newNode.outputStack = oldNode.outputStack
                            newNode.printer.append(oldNode.printer)
                            return newNode
                        }

                        // if so, break up that branch by copy/paste
                        fun copyWithNewLabels2(newNode: Node, mapping: HashMap<Label, Label>): Node {
                            mapping[newNode.label] = newNode.label
                            // not done, generate new node with new label
                            // replace all references
                            newNode.ifFalse =
                                if (newNode.ifFalse != null) copyWithNewLabels1(newNode.ifFalse, mapping) else null
                            newNode.ifTrue =
                                if (newNode.ifTrue != null) copyWithNewLabels1(
                                    labelToNode[newNode.ifTrue]!!,
                                    mapping
                                )!!.label else null
                            // copy all data
                            newNode.isAlwaysTrue = newNode.isAlwaysTrue
                            newNode.isReturn = newNode.isReturn
                            newNode.inputStack = newNode.inputStack
                            newNode.outputStack = newNode.outputStack
                            return newNode
                        }

                        if (printOps) println("breaking up branches at [${node.index}]")
                        // printState()
                        val mapping = HashMap<Label, Label>()
                        // break up node by copying a branch
                        val newNode: Node
                        val oldNode: Node
                        if (trueSize <= falseSize) {
                            // copy true
                            oldNode = labelToNode[node.ifTrue]!!
                            newNode = if (oldNode.inputs.size == 1) {
                                copyWithNewLabels2(oldNode, mapping)
                            } else {
                                copyWithNewLabels1(oldNode, mapping)!!
                            }
                            node.ifTrue = newNode.label
                        } else {
                            // copy false
                            oldNode = node.ifFalse!!
                            newNode = if (oldNode.inputs.size == 1) {
                                copyWithNewLabels2(oldNode, mapping)
                            } else {
                                copyWithNewLabels1(oldNode, mapping)!!
                            }
                            node.ifFalse = newNode
                        }

                        // better than worrying about logic and correctness ^^
                        recalculateInputs()

                        changed2 = true
                        if (printOps) printState()

                    } catch (ignored: FoundEnd) {
                        // no, this branch has multiple exits
                    }
                }
            }

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
        "i32.lt_s\n", "i32.ge_s\n",
        "i32.le_s\n", "i32.gt_s\n",
        // todo why is this an issue?
        // "i32.eq\n", "i32.ne\n",
        "i64.lt_s\n", "i64.ge_s\n",
        "i64.le_s\n", "i64.gt_s\n",
        "i64.eq\n", "i64.ne\n",
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
        fun replace(a: String, b: String) {
            printer.size -= a.length
            printer.append(b)
            printer.size--
            printer.append(" ;; xxx\n")
        }
        for (i in equalPairs.indices step 2) {
            val a = equalPairs[i]
            val b = equalPairs[i + 1]
            if (printer.endsWith(a)) {
                replace(a, b)
                return
            } else if (printer.endsWith(b)) {
                replace(b, a)
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
        printer.append("  i32.eqz ;; xxx\n")
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
        vs.varPrinter.append("(local \$lbl i32)\n")
        if (true) {
            val code = createLargeSwitchStatement1(sig, nodes0, labelToNode, vs)
            for (i in code.lastIndex downTo 0) {
                vs.varPrinter.append(code[i])
            }
        } else {
            val code = createLargeSwitchStatement2(sig, nodes0, labelToNode, null, vs)
            vs.varPrinter.append(code)
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
            if (node.isReturn) {
                printer.append("unreachable\n")
            } else {
                // todo if either one is exitNode, jump to end of switch() block
                if (node.isBranch) {
                    // set end label
                    val trueIndex = labelToNode[node.ifTrue]!!.index
                    val falseIndex = node.ifFalse!!.index
                    // todo check, which one is faster ^^ (no, implement the paper, so we don't have to use hundreds of switch-statements)
                    if (false) {// use a "branch-less" formula ^^, probably useless
                        printer.append("  i32.const 0 i32.ne i32.const ").append(trueIndex - falseIndex)
                            .append(" i32.mul i32.const ").append(falseIndex)
                            .append(" i32.add local.set \$lbl\n")
                    } else {
                        printer.append("  (if (result i32) (then i32.const ")
                            .append(trueIndex)
                            .append(") (else i32.const ")
                            .append(falseIndex)
                            .append(")) local.set \$lbl\n")
                    }
                } else {
                    // set end label
                    val next = labelToNode[node.next]!!.index
                    printer.append("  i32.const ").append(next).append(" local.set \$lbl\n")
                }
                // store stack
                val outputs = node.outputStack
                if (!outputs.isNullOrEmpty()) {
                    if (comments) printer.append("  ;; store stack\n")
                    for ((idx, type) in outputs.withIndex().reversed()) {
                        printer.append("  local.set ").append(vars.getStackVarName(idx, type)).append('\n')
                    }
                }
            }
        }

        val loopIdx = loopIndex++

        if (firstIsLinear) {
            if (comments) printer.append("  ;; execute -1\n")
            printer.append(firstNode.printer, 0)
            finishBlock(firstNode)
        } else {
            printer.append("i32.const ")
                .append(nodes.first().index)
                .append(" local.set \$lbl\n")
        }

        printer.append(";; #").append(graphId).append(" \n")
        printer.append("(loop \$b").append(loopIdx).append("\n  ")
        for (i in nodes.indices) {
            printer.append("(block ")
        }
        printer.append("\n  (block local.get \$lbl (br_table")
        for (i in nodes.indices) {
            printer.append(' ').append(i)
        }
        printer.append("))\n")
        fun appendBlock(node: Node) {
            var dropCtr = 0
            while (true) {
                val ix = 1 + 5 * dropCtr
                if (!node.printer.startsWith(Drop, ix)) break
                dropCtr++
            }
            // load stack
            var s0 = 0
            val inputs = node.inputStack
            if (inputs != null && inputs.isNotEmpty()) {
                if (comments) printer.append("  ;; load stack\n")
                for (idx in 0 until inputs.size - dropCtr) {
                    val type = inputs[idx]
                    printer.append("  local.get ").append(vars.getStackVarName(idx, type)).append('\n')
                }
                val dropped = min(inputs.size, dropCtr)
                if (dropped > 0) {
                    s0 = 1 + 5 * dropped
                }
            }
            if (node != exitNode) {
                // execute
                if (comments) printer.append("  ;; execute ${node.index}\n")
                printer.append(node.printer, s0)
                finishBlock(node)
                // close block
                printer.append("br \$b").append(loopIdx)
            }
            printer.append(") ;; block end\n")// else exit switch statement :)
        }
        for (node in nodes) {
            appendBlock(node)
        }
        if (exitNode != null) {
            appendBlock(exitNode)
        }
        if (exitNode == null) {
            // close loop
            printer.append(") unreachable\n")
        } else {
            printer.append(")\n")
        }
        return printer
    }

    class StackVariables {
        val varPrinter = Builder(64)
        val stackVariables = HashSet<String>()
        fun getStackVarName(i: Int, type: String): String {
            val name = "\$s$i$type"
            if (stackVariables.add(name)) {
                varPrinter.append("  (local $name $type)\n")
            }
            return name
        }
    }

    /// todo there is a small set of near-exit nodes, that we are allowed to replicate: return true/false

    private fun createLargeSwitchStatement1(
        sig: MethodSig,
        nodes0: List<Node>,
        labelToNode: Map<Label, Node>,
        vars: StackVariables
    ): ArrayList<Builder> {
        // find all nodes, that separate the graph
        if (true) for (separator in nodes0) {
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

    object FoundEnd : Throwable()

}

