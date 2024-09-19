package dependency

import entrySig
import hIndex
import utils.MethodSig
import utils.dynIndexSig
import utils.methodName

object ActuallyUsedIndex {

    private val isLocked get() = resolved.isNotEmpty()
    val usedBy = HashMap<String, HashSet<String>>()
    val uses = HashMap<String, HashSet<String>>()

    fun add(caller: MethodSig, called: MethodSig) {
        if (caller.clazz == "kotlin/jvm/internal/PropertyReference1") {
            println("Method-Translating/2: $caller -> $called")
        }
        val callerName = methodName(caller)
        val calledName = methodName(called)
        val changed0 = usedBy.getOrPut(calledName) { HashSet() }.add(callerName)
        val changed1 = uses.getOrPut(callerName) { HashSet() }.add(calledName)
        if ((changed0 || changed1) && isLocked) {
            throw IllegalStateException("Cannot add dependencies after resolution!, $callerName -> $calledName")
        }
        if (changed0 || changed1) {
            if (called in hIndex.abstractMethods) {

            }
        }
    }

    val resolved: HashSet<String> = HashSet()
    fun resolve(): HashSet<String> {
        val resolved = resolved
        val todoList = ArrayList<String>()
        fun todo(sig: String) {
            if (resolved.add(sig)) {
                todoList.add(sig)
            }
        }
        todo(methodName(dynIndexSig))
        todo(methodName(entrySig))
        while (todoList.isNotEmpty()) {
            val sig = todoList.removeLast()
            val uses1 = uses[sig]
            if (uses1 != null) {
                for (sig1 in uses1) {
                    todo(sig1)
                }
            }
        }
        return resolved
    }
}