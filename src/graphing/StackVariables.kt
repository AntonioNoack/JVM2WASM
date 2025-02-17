package graphing

import utils.Builder
import wasm.parser.LocalVariable

class StackVariables {
    val varPrinter = Builder(64)
    val stackVariables = HashSet<String>()
    fun getStackVarName(i: Int, type: String): String {
        val name = "\$s$i$type"
        if (stackVariables.add(name)) {
            varPrinter.localVariables.add(LocalVariable(name, type))
        }
        return name
    }
}