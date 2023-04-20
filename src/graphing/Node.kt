package graphing

import org.objectweb.asm.Label
import utils.Builder

class Node(val label: Label) {

    var index = -1
    val inputs = HashSet<Node>()

    var ifFalse: Node? = null
    var ifTrue: Label? = null
    var isAlwaysTrue = false
    var isReturn = false // mmh
    val next get() = if (isAlwaysTrue) ifTrue else ifFalse?.label

    val isBranch get() = ifTrue != null && ifFalse != null && !isAlwaysTrue

    var inputStack: List<String>? = null
    var outputStack: List<String>? = null

    var printer = Builder(32)

    var hasNoCode = false
    fun calcHasNoCode() = printer.length < 256 &&
            printer.split('\n').all {
                val ix = it.indexOf(";;")
                if (ix >= 0) {
                    it.substring(0, ix)
                        .isBlank()
                } else {
                    it.isBlank()
                }
            }

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