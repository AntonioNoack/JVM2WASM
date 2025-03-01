package graphing

import utils.Builder

class BranchNode(printer: Builder) : GraphingNode(printer) {

    lateinit var ifTrue: GraphingNode
    lateinit var ifFalse: GraphingNode

    override val outputs get() = listOf(ifTrue, ifFalse)
    override fun toString(): String {
        return "[$index ? ${ifTrue.index} : ${ifFalse.index}]"
    }
}