package graphing

import utils.Builder

class SequenceNode(printer: Builder) : GraphingNode(printer) {

    constructor(printer: Builder, next: GraphingNode) : this(printer) {
        this.next = next
    }

    lateinit var next: GraphingNode

    override val outputs get() = listOf(next)
    override fun toString(): String {
        return "[$index -> ${next.index}]"
    }
}