package graphing

import org.objectweb.asm.Label
import utils.Builder
import wasm.instr.Comment

class Node(val label: Label) {

    var index = -1
    val inputs = HashSet<Node>()

    var ifTrue: Label? = null
    var ifFalse: Node? = null

    var isAlwaysTrue = false
    var isReturn = false

    val next get() = if (isAlwaysTrue) ifTrue else ifFalse?.label
    val isBranch get() = ifTrue != null && ifFalse != null && !isAlwaysTrue

    var inputStack: List<String> = emptyList()
    var outputStack: List<String> = emptyList()

    var printer = Builder()

    var hasNoCode = false
    fun calcHasNoCode(): Boolean = printer.instrs.all { it is Comment }

    fun toString(mapper: (Label?) -> String): String {
        val name = mapper(label)
        return if (isAlwaysTrue) {
            "[$name -> ${mapper(ifTrue)}]"
        } else if (isReturn) {
            "[$name -> exit]"
        } else if (ifTrue != null) {
            "[$name ? ${mapper(ifTrue!!)} : ${mapper(ifFalse!!.label)}]"
        } else if (ifFalse == null) {
            "[$name -?> void]"
        } else {
            "[$name -> ${mapper(ifFalse!!.label)}]"
        }
    }

    override fun toString(): String {
        return toString { it!!.toString() }
    }

}