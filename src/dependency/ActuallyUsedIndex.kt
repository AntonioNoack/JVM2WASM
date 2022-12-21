package dependency

import entrySig
import utils.methodName
import utils.MethodSig
import utils.dynIndexSig

object ActuallyUsedIndex {

    val usedBy = HashMap<String, HashSet<String>>()
    val uses = HashMap<String, HashSet<String>>()

    fun add(caller: MethodSig, called: String) {
        if(called == "me_anno_ecs_prefab_PrefabSaveable_simpleTraversal_ZLkotlin_jvm_functions_Function1Lme_anno_utils_structures_Hierarchical")
            TODO("$caller -> $called")
        add(methodName(caller), called)
    }

    fun add(caller: String, called: String) {
        usedBy.getOrPut(called) { HashSet() }.add(caller)
        uses.getOrPut(caller) { HashSet() }.add(called)
    }

    var resolved: HashSet<String> = HashSet()
    fun resolve(): HashSet<String> {
        val resolved = HashSet<String>()
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
        this.resolved = resolved
        return resolved
    }
}