package utils

import dIndex
import gIndex
import hIndex
import me.anno.utils.structures.maps.CountMap

val methodVariants = HashMap<MethodSig, HashSet<MethodSig>>()

fun getMethodVariants(sig: MethodSig): Set<MethodSig> {
    return if (sig.clazz in dIndex.constructableClasses) {
        methodVariants.getOrPut(sig) {
            val variants = HashSet<MethodSig>()
            val mapped = hIndex.getAlias(sig)
            if (mapped in hIndex.jvmImplementedMethods || mapped in hIndex.customImplementedMethods) {
                variants.add(mapped)
            }
            val children = hIndex.childClasses[sig.clazz]
            if (children != null) {
                for (child in children) {
                    variants.addAll(getMethodVariants(sig.withClass(child)))
                }
            }
            variants
        }
    } else emptySet()
}

fun countMethodVariants(sig: MethodSig): Int {
    return getMethodVariants(sig).size
}

fun markChildMethodsFinal(sig: MethodSig) {
    if (sig.clazz in dIndex.constructableClasses) {
        hIndex.finalMethods.add(sig)
        val children = hIndex.childClasses[sig.clazz] ?: return
        for (childClass in children) {
            markChildMethodsFinal(sig.withClass(childClass))
        }
    }
}

fun findMethodsWithoutChildClasses(): Int {

    val finalMethods0 = hIndex.finalMethods.size
    for (clazz in gIndex.classNames) {
        if (clazz !in dIndex.constructableClasses) continue
        if (hIndex.childClasses[clazz]?.any { it in dIndex.constructableClasses } != true) {
            // no constructable child classes -> all its methods must be final
            val methods = hIndex.methods[clazz] ?: continue
            hIndex.finalMethods.addAll(methods)
        } else if (clazz in dIndex.constructableClasses) {
            // if all children have the same implementation, it's final for all of them, too
            val methods = hIndex.methods[clazz] ?: continue
            for (method in methods) {
                if (method !in hIndex.finalMethods) {
                    if (countMethodVariants(method) == 1) {
                        // mark all child methods final
                        markChildMethodsFinal(method)
                    }
                }
            }
        }
    }
    return hIndex.finalMethods.size - finalMethods0
}

/**
 * find functions with a single implementation only, and make it final
 * */
fun findUniquelyImplemented(usedMethods: Collection<MethodSig>, implementedMethods: Collection<MethodSig>) {
    println("[findUniquelyImplemented]")

    // we only need this for classes, where multiple classes are constructable
    // otherwise, we can directly resolve the call :)

    // of all classes without constructable child classes,
    // all their methods are final
    var finalMethods = findMethodsWithoutChildClasses()

    val methodCounter = CountMap<InterfaceSig>(usedMethods.size)
    for (sig in usedMethods) {
        if (
            sig.name != "<init>" && // cannot be invoked dynamically
            sig.clazz != "?" &&
            hIndex.getAlias(sig) == sig && // just the same
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
        if (hIndex.getAlias(sig) == sig && // just the same
            sig !in hIndex.staticMethods &&
            sig !in hIndex.abstractMethods && // we can ignore that one
            InterfaceSig(sig) in toBeMarkedAsFinal
        ) {
            hIndex.finalMethods.add(sig)
        }
    }

    println("  found $finalMethods uniquely implemented methods and made them final :)")

    // HashSet and LinkedHashSet share the same implementation
    // printUsed(MethodSig.c("java/util/HashSet", "add", "(Ljava/lang/Object;)Z"))
    // printUsed(MethodSig.c("java/util/LinkedHashSet", "add", "(Ljava/lang/Object;)Z"))
    // assertTrue(MethodSig.c("java/util/HashSet", "add", "(Ljava/lang/Object;)Z") in hIndex.finalMethods)

}