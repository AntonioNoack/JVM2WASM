package graphing

import utils.Builder

class ReturnNode(printer: Builder) : GraphingNode(printer) {
    override val outputs get() = emptyList<GraphingNode>()
    override fun toString(): String {
        return "[$index -> exit]"
    }
}