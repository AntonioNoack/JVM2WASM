package utils

import dIndex
import gIndex
import hIndex
import me.anno.utils.structures.maps.CountMap

/**
 * find functions with a single implementation only, and make it final
 * */
fun findUniquelyImplemented(usedMethods: Collection<MethodSig>, implementedMethods: Collection<MethodSig>) {
    println("[findUniquelyImplemented]")

    // we only need this for classes, where multiple classes are constructable
    // otherwise, we can directly resolve the call :)

    // of all classes without constructable child classes,
    // all their methods are final
    var finalMethods = 0
    for (clazz in gIndex.classNames) {
        if (hIndex.childClasses[clazz]?.any { it in dIndex.constructableClasses } != true) {
            val methods = hIndex.methods[clazz] ?: continue
            for (method in methods) {
                if (hIndex.finalMethods.add(method)) { // finding new final methods :)
                    finalMethods++
                }
            }
        }
    }

    val methodCounter = CountMap<InterfaceSig>(usedMethods.size)
    for (sig in usedMethods) {
        if (
            sig.name != "<init>" && // cannot be invoked dynamically
            sig.clazz != "?" &&
            methodName(sig) !in hIndex.methodAliases && // just the same
            sig !in hIndex.staticMethods &&
            sig !in hIndex.abstractMethods && // we can ignore that one
            sig in implementedMethods
        ) {
            when (methodCounter.incAndGet(InterfaceSig(sig))) {
                1 -> finalMethods++
                2 -> finalMethods--
            }
        }
    }

    val toBeMarkedAsFinal = HashSet<InterfaceSig>(finalMethods)
    for ((sig, counter) in methodCounter.values) {
        if (counter.value == 1) {
            toBeMarkedAsFinal.add(sig)
        }
    }

    // mark all those methods as final
    for (sig in usedMethods) {
        if (methodName(sig) !in hIndex.methodAliases && // just the same
            sig !in hIndex.staticMethods &&
            sig !in hIndex.abstractMethods && // we can ignore that one
            InterfaceSig(sig) in toBeMarkedAsFinal
        ) {
            hIndex.finalMethods.add(sig)
        }
    }

    println("found $finalMethods uniquely implemented methods and made them final :)")

}