package dependency

import entrySig
import utils.DynIndex.dynIndexSig
import utils.MethodSig
import utils.methodName

object ActuallyUsedIndex {

    private val isLocked get() = resolved.isNotEmpty()
    val usedBy = HashMap<String, HashSet<String>>()
    val uses = HashMap<String, HashSet<String>>()

    fun add(caller: MethodSig, called: MethodSig) {
        val callerName = methodName(caller)
        val calledName = methodName(called)
        val changed0 = usedBy.getOrPut(calledName) { HashSet() }.add(callerName)
        val changed1 = uses.getOrPut(callerName) { HashSet() }.add(calledName)
        if ((changed0 || changed1) && isLocked) {
            throw IllegalStateException("Cannot add dependencies after resolution!, $callerName -> $calledName")
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