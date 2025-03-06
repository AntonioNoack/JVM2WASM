package graphing

import utils.Builder

abstract class GraphingNode(val printer: Builder) {

    lateinit var inputStack: List<String>
    lateinit var outputStack: List<String>

    var index = -1
    val inputs = HashSet<GraphingNode>()

    var hasNoCode = false

    abstract val outputs: List<GraphingNode>

}