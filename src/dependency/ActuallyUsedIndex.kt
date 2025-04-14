package dependency

import entrySig
import hIndex
import listEntryPoints
import utils.DynIndex.dynIndexSig
import utils.MethodSig
import utils.methodName

object ActuallyUsedIndex {

    private val isLocked get() = resolved.isNotEmpty()
    private val uses = HashMap<String, HashSet<String>>()
    val usedBy = HashMap<String, HashSet<String>>()

    fun add(caller: MethodSig, called: MethodSig) {
        val callerName = methodName(caller)
        val calledName = methodName(called)
        val changed0 = usedBy.getOrPut(calledName) { HashSet() }.add(callerName)
        val changed1 = uses.getOrPut(callerName) { HashSet() }.add(calledName)
        if ((changed0 || changed1) && isLocked) {
            throw IllegalStateException("Cannot add dependencies after resolution!, $callerName -> $calledName")
        }
    }

    fun add(caller: MethodSig, called: Collection<MethodSig>) {
        val callerName = methodName(caller)
        val calledName = called.map { calledI -> methodName(calledI) }
        val changed0 = calledName.any { calledNameI ->
            usedBy.getOrPut(calledNameI) { HashSet() }.add(callerName)
        }
        val changed1 = uses.getOrPut(callerName) { HashSet() }.addAll(calledName)
        if ((changed0 || changed1) && isLocked) {
            throw IllegalStateException("Cannot add dependencies after resolution!, $callerName -> $calledName")
        }
    }

    fun addEntryPointsToActuallyUsed() {
        listEntryPoints({
            for (sig in hIndex.methodsByClass[it]!!) {
                add(entrySig, sig)
            }
        }, { sig ->
            add(entrySig, sig)
        })
    }

    val resolved: HashSet<String> = HashSet()
    fun resolve(): HashSet<String> {
        val resolved = resolved
        val remaining = ArrayList<String>()
        fun addMaybe(sig: String) {
            if (resolved.add(sig)) {
                remaining.add(sig)
            }
        }
        addMaybe(methodName(dynIndexSig))
        addMaybe(methodName(entrySig))
        while (remaining.isNotEmpty()) {
            val sig = remaining.removeLast()
            val uses1 = uses[sig]
            if (uses1 != null) {
                for (sig1 in uses1) {
                    addMaybe(sig1)
                }
            }
        }
        return resolved
    }
}