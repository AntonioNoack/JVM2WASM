package utils

import dIndex
import gIndex
import hIndex
import me.anno.utils.structures.maps.CountMap

/**
 * find functions with a single implementation only, and make it final
 * */
fun findUniquelyImplemented(usedMethods: Collection<MethodSig>, implementedMethods: Collection<MethodSig>) {

    // we only need this for classes, where multiple classes are constructable
    // otherwise, we can directly resolve the call :)

    // of all classes without constructable child classes,
    // all their methods are final
    var finalMethods = 0
    for (clazz in gIndex.classNames) {
        if (hIndex.childClasses[clazz]?.any { it in dIndex.constructableClasses } != true) {
            val methods = hIndex.methods[clazz]
            if (methods != null) {
                for (m in methods) {
                    if (hIndex.finalMethods.add(m)) {
                        finalMethods++
                    }
                }
            }
        }
    }

    val methodCounter = CountMap<GenericSig>(usedMethods.size)
    for (sig in usedMethods) {
        if (
            sig.name != "<init>" && // cannot be invoked dynamically, as far as I know
            sig.clazz != "?" &&
            methodName(sig) !in hIndex.methodAliases && // just the same
            sig !in hIndex.staticMethods &&
            sig !in hIndex.abstractMethods && // we can ignore that one
            sig in implementedMethods
        ) {
            val key = GenericSig(sig)
            val v = methodCounter.incAndGet(key)
            if (v == 1) finalMethods++
            else if (v == 2) finalMethods--
        }
    }

    if (finalMethods > 0) {
        val toBeMarkedAsFinal = HashSet<GenericSig>(methodCounter.values.size)
        for ((sig, counter) in methodCounter.values) {
            if (counter.value == 1) {
                toBeMarkedAsFinal.add(sig)
            }
        }

        // mark all those methods as final
        if (toBeMarkedAsFinal.isNotEmpty()) {
            for (sig in usedMethods) {
                if (methodName(sig) !in hIndex.methodAliases && // just the same
                    sig !in hIndex.staticMethods &&
                    sig !in hIndex.abstractMethods && // we can ignore that one
                    sig !in hIndex.finalMethods &&
                    GenericSig(sig) in toBeMarkedAsFinal
                ) {
                    hIndex.finalMethods.add(sig)
                }
            }
        }
    }

    println("found $finalMethods uniquely implemented methods and made them final :)")

}